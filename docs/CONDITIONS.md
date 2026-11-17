# Conditional registration and dependent-bean pruning

[README](../README.md) | [User guide](GUIDE.md) | [Host integration](LOADING.md)

Weft evaluates registration conditions while discovering annotated beans. With conditional-exclusion tracking enabled, a bean that needs an explicitly **condition-excluded** dependency can itself be omitted before startup. This omission can propagate to its own dependents.

These features require the Weft build containing `ConditionalExclusions`, `ConditionalDependencyPruner`, and the method-level `@Condition` annotation. They are **not present in the public `master` source at the time this documentation was drafted**; publish this guide alongside the matching implementation, not as a promise about older binaries.

## Which mechanism should I use?

| Mechanism | Purpose | Effect |
| --- | --- | --- |
| `@ConditionalOnEnv` | Enable a scanned bean when configuration keys are present or match a value. | Excludes its definition when the condition fails. |
| `@ConditionalOnProperty` | Enable a scanned bean based on named properties. | Excludes its definition when the condition fails. |
| `@Condition` | Compute an enablement decision in a static Java method. | Excludes its owning scanned bean/configuration when the method returns `false`. |
| `@ConditionalOnMissingBean` | Offer an alternative implementation when no matching contract definition exists. | Selects a fallback during registry freeze; **not** a dependent-bean skip condition. |
| `ConditionalDependencyPruner` | Prevent startup failures caused specifically by condition-excluded required dependencies. | Removes the dependent definition and repeats until no more omissions are needed. |

The behavior above applies to **annotation discovery**. `builder.register(SomeClass.class)` creates an explicit constructor definition; it does not interpret the class's registration conditions or create an exclusion record.

## `@ConditionalOnEnv`

```java
package example.cluster;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;

@Bean
@ConditionalOnEnv(name = "CFG_CLUSTER_INDEX")
public class ClusterManager {
    @Env(value = "CFG_CLUSTER_INDEX", defaultValue = "-1")
    int clusterIndex;
}
```

When `CFG_CLUSTER_INDEX` cannot be found by the bootstrap property resolver, `ClusterManager` is **not registered**. The `@Env(defaultValue = "-1")` value is a binding fallback for a created object; it does **not** make the variable present for `@ConditionalOnEnv`.

Both `@ConditionalOnEnv` and `@ConditionalOnProperty` use Weft's bootstrap property source, not an independent live environment lookup. Values are resolved from builder-provided properties, JVM system properties, and environment variables, in that order. The annotation's binding default is considered only for binding.

By default, `name` must be present, and an empty `havingValue` accepts a present value except the literal `false` (case-insensitive). You can supply `havingValue` to require an exact string, or `matchIfMissing = true` where appropriate. With multiple names, all keys must match.

```java
@Bean
@ConditionalOnEnv(name = "FEATURE_ENABLED", havingValue = "true")
public class OptionalFeature {
}
```

## `@ConditionalOnProperty`

```java
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;

@Bean
@ConditionalOnProperty(prefix = "weather", name = "sync", havingValue = "true")
public class WeatherSyncManager {
}
```

The key above is `weather.sync`. Enable the bean with a bootstrap option such as:

```java
var runtime = Weft.builder()
        .property("weather.sync", "true")
        .scan(scanSource)
        .start();
```

`scanSource` is an application-provided `ScanSource`. Missing properties do not match unless `matchIfMissing = true` is specified. Conditions are evaluated during bootstrap, not each time `runtime.get()` is called.

## Static-method `@Condition`

A scanned bean can declare a static, zero-argument method annotated with `@Condition`. It returns whether the bean should be registered.

```java
package example.integration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.condition.Condition;

@Bean
public class RegionIntegration {
    @Condition
    public static boolean isEnabled() {
        return RegionProtection.isInstalled();
    }
}
```

In the illustrated implementation, a condition method must be **public**, **static**, have **no parameters**, and return primitive `boolean`. It is evaluated without constructing the bean; it cannot depend on injected instance fields or `@PostConstruct`. Returning `false` records a conditional exclusion. An invalid signature or invocation failure is an error, not a request to skip silently.

Use a single `@Condition` method per class hierarchy; duplicate condition methods are invalid. A `@Configuration` owner can use the same class-level pattern to gate its factory beans. You may combine method-based and declarative registration conditions; all applicable checks must pass.

If a condition calls platform APIs (for example, a NeoForge mod list), ensure they are available **at discovery time**. Weft does not reevaluate a condition when the platform state changes later.

## Transitive `SKIPPED`: an optional subsystem

A condition-excluded bean is tracked, along with its type, key, name, and exclusion reason. The pruning pass sees which other active beans require that type.

```java
package example.cluster;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Inject;

@Bean
public class WeatherTimeRedisListener {
    @Inject
    ClusterManager clusterManager;
}

@Bean
public class ClusterEventCoordinator {
    @Inject
    WeatherTimeRedisListener listener;
}
```

Assuming no alternative bean satisfies either required dependency:

```text
CFG_CLUSTER_INDEX is missing
  ClusterManager           -> condition EXCLUDED
  WeatherTimeRedisListener -> SKIPPED (requires ClusterManager)
  ClusterEventCoordinator  -> SKIPPED (requires WeatherTimeRedisListener)
  Other unrelated beans    -> remain active
```

The pruning pass runs to a fixed point: newly skipped beans can cause other dependent beans to be skipped in a subsequent pass. It logs `WARN` messages such as `SKIPPED bean <key>: required ... depends on excluded ...`.

**This is not a generic ignore-missing-dependencies mode.** A required dependency that is absent because it was never registered, has an incorrect qualifier, or cannot be found for another ordinary reason still produces the normal resolution error.

### Injection kinds

| Dependency request | Causes dependent `SKIPPED` when its only matching target was condition-excluded? |
| --- | --- |
| Direct `T` | Yes. |
| `Provider<T>` | Yes. It still needs a resolvable target definition. |
| `@Lazy T` | Yes. The proxy still needs a resolvable target definition. |
| `Optional<T>` | No; it can resolve to `Optional.empty()`. |
| `List<T>` / `Set<T>` | No; missing candidates yield an empty collection. |
| `@Property` / `@Env` value | No; property binding has separate validation and defaults. |

For direct, provider, and lazy dependencies, the pruner first uses normal candidate resolution. If another active bean can satisfy the dependency, omission is not automatically necessary. Matching also respects the dependency's type, bean name, and explicit target key; it is not a simple class-name string comparison. An explicitly configured source default pointing at an excluded target can itself cause a dependent to be skipped.

Pruning applies to **registered definitions** at bootstrap. It does not automatically remove external objects injected later through `runtime.inject(...)`, unregistered objects constructed through `runtime.create(...)`, or dependencies first discovered from the runtime class of an object returned by a factory. Those still use normal runtime dependency resolution.

## `@ConditionalOnMissingBean` is different

Use a fallback when the application should keep running **with an alternative implementation**:

```java
// The interface and implementations belong in separate top-level files.
public interface StorageService {
    void save();
}

@Bean
public class RedisStorageService implements StorageService {
    @Override public void save() { /* ... */ }
}

@Bean
@ConditionalOnMissingBean(StorageService.class)
public class LocalStorageService implements StorageService {
    @Override public void save() { /* ... */ }
}
```

The registry's fallback selector considers the available definition types at **registry freeze**, before dependent-bean pruning. If a primary bean was excluded during annotation discovery, it is absent from this selection; the fallback can be chosen. If a primary definition is removed *later* by pruning, fallback selection is **not rerun**.

`@ConditionalOnMissingBean(StorageService.class)` belongs on a class that implements or extends that contract (or on an appropriate factory definition). A `@Configuration` annotated as a fallback must specify explicit contract classes. This annotation does not mean "skip the current bean when the named dependency is missing".

For a disabled cluster subsystem, choose between these deliberate designs:

- **Entire subsystem is optional:** `@ConditionalOnEnv` on `ClusterManager` and transitive dependent `SKIPPED`.
- **Subsystem always exists but has a no-op mode:** expose an interface and register a fallback implementation with `@ConditionalOnMissingBean`.

## Startup order

```text
1. Collect explicit definitions, contributors, and ScanSource inputs.
2. Scan classes and evaluate registration conditions.
3. Record condition-excluded definitions and reasons.
4. Expand source-specific defaults.
5. Freeze the registry and select @ConditionalOnMissingBean fallbacks.
6. Prune active definitions requiring condition-excluded dependencies.
   Repeat until no new SKIPPED beans are found.
7. Build DependencyResolver and initialization plan from survivors.
8. Create eager beans and run lifecycle hooks.
```

Because fallback selection and dependent-bean pruning are separate passes, do not assume a fallback will be discovered or reconsidered after a later transitive skip.

## Embedded hosts: preserve exclusion metadata

The recommended path for a host that supplies its own class loader is to pass physical scan roots into the builder:

```java
import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.ScanSource;
import java.nio.file.Path;
import java.util.List;

// jarPath (Path), platformLoader (ClassLoader), and moduleId (String)
// are supplied by the host integration.
var builder = Weft.builder()
        .scan(new ScanSource(jarPath,
                new BeanSource(moduleId, platformLoader),
                List.of()));
```

The host can cheaply prefilter its modules (for example, using NeoForge annotation scan data) and add only relevant module JARs as `ScanSource`. The final annotation reader and condition tracker must still run in Weft's bootstrap. Velocity can submit plugin JAR paths in the same manner.

**Avoid this pattern when dependent `SKIPPED` matters:**

```java
for (var definition : new AnnotationDefinitionReader().read(type, source)) {
    builder.register(definition);
}
```

The reader can decline to return a definition, but this approach transfers only the *surviving* definitions. It does not transfer `ConditionalExclusions` entries to Weft. Consequently, an otherwise valid dependent bean may fail with `MissingBeanException` because the runtime cannot distinguish conditional absence from a configuration error.

Also note that `ScanSource` requires an accessible directory or ordinary/multi-release JAR root; Weft does not walk platform-specific nested JARs. The class loader must already resolve the classes at that root. Keep a source ID paired consistently with one root and loader, and deploy one version of each module.

## Diagnosing unexpected `MissingBeanException`

Check the sequence, not just the exception message:

1. Was the condition-bearing class discovered through `ScanSource`, or manually converted to a `BeanDefinition` outside Weft?
2. Was its condition actually false against the **bootstrap** property snapshot?
3. Did `ConditionalExclusions` contain the omitted bean (type, name, source, reason)?
4. Did an active candidate, name, key, or source default change dependency selection?
5. Is the running Weft JAR the build containing the pruning pass? Is the host loading one version of each mod/plugin?
6. Do the class loaders agree on the same dependency contract type?

The pruner's `WARN` messages identify beans omitted because of excluded dependencies. Optional diagnostic builds may expose additional tracing, but such debug flags are not part of the stable Weft API.

## Known boundaries

- Conditions are evaluated during bootstrap, not after the runtime is running.
- Class-level registration conditions require annotation discovery; plain `register(Class)` does not evaluate them.
- Conditional `SKIPPED` is tied to recorded exclusion reasons, not arbitrary missing definitions.
- No automatic fallback reselection occurs after dependent-bean pruning.
- Cross-loader type identity, nested JAR support, and source-scoped resolution are separate constraints; pruning does not bypass them.
- Prototype instances, external injection, and late-discovered concrete implementation dependencies are still resolved when used.

See [Using Weft](GUIDE.md) for other injection kinds, lifecycle rules, and source defaults, and [Host integration](LOADING.md) for root/class-loader boundaries.
