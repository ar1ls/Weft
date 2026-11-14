# Using Weft

[← README](../README.md)

Weft separates bean definitions from running objects. You collect definitions in a builder, start a runtime, and close it when the application stops. Definitions are fixed after startup; lookups do not register new beans.

This guide uses the public API in the current source tree and requires Java 21. Snippets are focused examples; application types such as `Repository` represent your own classes unless defined in the snippet.

## Contents

- [Registration and scanning](#registration-and-scanning)
- [Host loaders and module integration](LOADING.md)
- [Dependency selection](#dependency-selection)
- [Scopes and deferred dependencies](#scopes-and-deferred-dependencies)
- [Properties and conditions](#properties-and-conditions)
- [Lifecycle and ownership](#lifecycle-and-ownership)
- [Source-specific defaults](#source-specific-defaults)
- [Extensions and diagnostics](#extensions-and-diagnostics)
- [Runtime boundaries](#runtime-boundaries)

## Registration and scanning

### Explicit definitions

```java
import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.definition.TypeRef;
import java.time.Clock;
import java.util.UUID;

var builder = Weft.builder()
        .register(Definitions.instance("clock", Clock.class, Clock.systemUTC(), false))
        .register(Definitions.supplied("operation-id", TypeRef.of(UUID.class),
                BeanScope.PROTOTYPE, false, UUID::randomUUID));

try (var runtime = builder.start()) {
    var clock = runtime.get(Clock.class);
    var operationId = runtime.get(UUID.class);
}
```

The first argument to `instance` or `supplied` is a definition ID, not an injection qualifier. These helpers use the source ID `programmatic` and an empty bean name. `register(SomeClass.class, "billing")` and `Definitions.of(SomeClass.class, "billing")` assign a source to a constructor-based singleton; `Definitions.of(typeRef, "billing")` also supports generic types. The no-source overloads retain `programmatic`. Explicit source IDs must be non-null and nonblank. Use an explicit `BeanDefinition` when you need control over the full key, name, primary flag, or factory.

`register(SomeClass.class)` creates a constructor-based singleton definition. It does not interpret class-level scope, lazy, or registration-condition annotations and does not expand `@Configuration` factory methods. Property binding, field injection, and lifecycle handling still apply.

Bean classes must be public and concrete. Weft uses one public constructor marked `@Inject`, or otherwise requires exactly one public constructor. Multiple `@Inject` constructors and ambiguous public constructors are invalid; there is no automatic no-argument fallback when several public constructors exist.

### Annotation discovery

Scanning recognizes `@Bean` classes and `@Configuration` classes with `@Bean` methods. Put each scanned class in its own top-level Java declaration. For example, a configuration can define a shared clock:

```java
package example.app;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import java.time.Clock;

@Configuration
public class TimeConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

Supply its scan location and class loader explicitly:

```java
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.ScanSource;
import java.nio.file.Path;
import java.util.List;

var anchor = TimeConfiguration.class;
var root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
var source = new ScanSource(root,
        new BeanSource("app", anchor.getClassLoader()),
        List.of("example.app"), List.of("example.app.internal.disabled"));

try (var runtime = Weft.builder().scan(source).start()) {
    var clock = runtime.get(java.time.Clock.class);
}
```

A root can be a directory, an ordinary JAR, or a multi-release JAR. An empty package list scans the entire root. The scanner does not support nested/fat JAR layouts or nested `$` classes. It closes its own read streams, but never the supplied class loader.

The same source ID must use the same root and loader across scan entries. Overlapping scans within that source deduplicate discovered classes; exclusions apply separately to each scan entry. Explicit duplicate bean keys remain errors.

Factory method parameters are dependencies. If one `@Bean` method needs another bean, declare a parameter for it. Weft does not intercept direct calls between configuration methods.

For complete examples of passing an existing application or platform loader, scanning module JARs, sharing host contracts, and applying source context to external objects, see [Integrating host classes and modules](LOADING.md). The concrete [ModuleHostExample](../src/test/java/com/github/ar1ls/weft/sandbox/loading/ModuleHostExample.java) supplies the host and module implementations.

## Dependency selection

Constructor parameters and fields marked `@Inject` can request:

| Request | Resolution |
| --- | --- |
| `T` | One candidate, or an error if missing or ambiguous. |
| `Optional<T>` | Empty if missing; ambiguity is still an error. |
| `List<T>` / `Set<T>` | All matching candidates in bean-key order; immutable. |
| `Provider<T>` | A deferred lookup of one selected candidate. |
| `@Lazy T` | A deferred proxy for a non-sealed interface. |

`Provider` is `com.github.ar1ls.weft.api.Provider`. Nested dependency wrappers such as `Provider<List<T>>` are unsupported.

Use `@Named("name")` on a scanned bean or factory method and on its consumer to select a named candidate. Explicit names and target keys bypass automatic source preference. An applicable source default also takes precedence. Otherwise, unnamed single-target dependencies first consider candidates with the consumer's source ID; only when that group is empty do they consider all sources. `@Primary` selects within the chosen group, so an external primary cannot override a local candidate or resolve ambiguity between local candidates. More than one local candidate still requires one local primary or explicit selection.

This rule applies to constructor, field, and factory-method injection, including optional, provider, and lazy dependencies. Lists and sets keep all matching sources. Top-level `get` has no consumer source and retains global selection. For external objects, `create(type, sourceId)` and `inject(instance, sourceId)` supply the consumer context explicitly; without it, selection remains global. Source IDs group candidates rather than isolating them: an unknown source still falls back to ordinary global selection.

Fields marked `@Inject` must be non-static and non-final. Constructor injection is useful when you want final fields and dependencies visible in the type's API.

### Generic contracts

```java
import com.github.ar1ls.weft.definition.TypeRef;

// Repository<T> is an application contract with a registered implementation.
TypeRef<Repository<String>> type =
        TypeRef.parameterized(Repository.class, TypeRef.of(String.class));
Repository<String> repository = runtime.get(type);
```

Use an explicit target type for `TypeRef.parameterized`; `var` does not provide its result's generic type argument. Closed, invariant generic types and substitutions through type hierarchies are supported. Wildcards and unresolved type variables are rejected. A raw `Class` lookup uses assignability without filtering generic arguments.

A factory's declared return type defines its lookup contract. A method returning `Repository` does not also register its result's concrete implementation class as a separate candidate.

## Scopes and deferred dependencies

Singleton is the default scope. A singleton is created once per runtime; prototypes are created for each resolution. With scanning, annotate a bean or factory method with `@Scope(BeanScope.PROTOTYPE)` to select prototype scope.

`@Lazy` on a definition postpones singleton creation until requested. An eager bean with a direct dependency on it still causes creation at startup. Use a provider or a lazy injection point to defer that dependency too.

For example, a registered `ReportService` can accept a provider:

```java
import com.github.ar1ls.weft.api.Provider;

public class ReportService {
    private final Provider<ReportSession> sessions;

    public ReportService(Provider<ReportSession> sessions) {
        this.sessions = sessions;
    }

    public void generate() {
        // ReportSession is an application-defined AutoCloseable prototype.
        try (var session = sessions.get()) {
            session.generate();
        }
    }
}
```

For a singleton target, repeated `get()` calls return the same object. For a prototype target, they create new objects. Calling a provider during construction can still produce a creation cycle; deferral only helps if the lookup happens later.

`@Lazy` injection uses JDK interface proxies. Each business call resolves the target according to its scope, so a prototype target is recreated for each call. `equals`, `hashCode`, and `toString` do not instantiate the target. Concrete-class proxies are unsupported.

## Properties and conditions

Property values come from a snapshot captured at bootstrap, in this order:

1. Values passed through `builder.property(key, value)`.
2. JVM system properties.
3. Environment variables.
4. The annotation's default value.

Use explicit property names on constructor or factory parameters, or enable Java's `-parameters` option for inferred names. Record components preserve their names automatically.

```java
package example.app;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import java.time.Duration;

@Bean
@Properties("http")
public record HttpSettings(
        @Property(defaultValue = "https://example.com") String endpoint,
        @Property(defaultValue = "PT5S") Duration timeout) {
}
```

With this record discovered by scanning, `.property("http.timeout", "PT10S")` overrides the timeout. Mutable settings classes with bindable fields are also supported. `@Env` uses an absolute key rather than the `@Properties` prefix.

Supported scalar types include strings, primitives and wrappers, enums, ISO-8601 `Duration`, and `Path`. Scalar lists and sets use comma-separated values; `Map<String, T>` uses `key=value` entries separated by commas. Collections are immutable. Nested property objects and records are supported; collections of records, nested collections, and escaped commas are not.

### Conditional registration

On scanned beans, factory methods, or configurations:

```java
@Bean
@ConditionalOnProperty(prefix = "app", name = "mode", havingValue = "production")
public MessageFormatter productionFormatter() {
    return new ProductionFormatter();
}
```

Here `MessageFormatter` and `ProductionFormatter` are application types; import the condition from `com.github.ar1ls.weft.api.annotations.condition`.

`@ConditionalOnEnv` matches environment conditions. `@ConditionalOnMissingBean` provides a fallback based on the active definition types at registry freeze; it does not instantiate candidates or use names and primary flags for its check. A condition on an entire fallback configuration requires explicit contract classes. Conditions are not reevaluated at runtime.

## Lifecycle and ownership

New managed objects follow this sequence:

```text
constructor / factory
    → property binding
    → @Inject fields
    → @PostConstruct
    → BeanProcessor chain
    → exposed object
```

`@PostConstruct` and `@PreDestroy` methods are instance methods with no arguments and a `void` return type. After eager creation, singleton beans declared with a `StartupHook` contract receive `onStartup()`, including lazy hooks. Prototype hooks do not participate in startup.

At shutdown, owned singletons receive `@PreDestroy` and then `AutoCloseable.close()` when applicable. Consumers are destroyed before their dependencies. The runtime cleans up the original object even when a processor exposes a wrapper; the processor owns any resources added by that wrapper.

| API | Initialization | Cleanup after success |
| --- | --- | --- |
| Registered singleton | Full pipeline | Runtime, when owned. |
| Registered prototype | Full pipeline | Caller. |
| `runtime.create(Type.class)` | Full pipeline; no registry entry | Caller. |
| `runtime.inject(instance)` | Only `@Inject` fields | Caller or external host. |
| `Definitions.instance(..., false)` | Already ready; processors still apply | External owner. |
| `Definitions.instance(..., true)` | Already ready; processors still apply | Runtime. |

Existing instances are not rebound, reinjected, or sent through `@PostConstruct`. If creation fails after an owned object has been returned by its factory, Weft attempts immediate cleanup. Successful prototype and `create` results are not retained for shutdown.

When a factory returns a concrete implementation behind an interface, Weft prepares that implementation's fields, properties, and lifecycle metadata before publishing it. Those late-discovered dependencies are validated at creation time.

### Ready notifications

```java
try (var subscription = runtime.subscribe(event ->
        System.out.println("Ready: " + event.definition().key()))) {
    // Existing ready singletons are replayed, then newly ready singletons arrive.
    // Keep the subscription open for as long as the application needs it.
}
```

Events carry the exposed singleton instance. Callbacks are serialized per subscription and run outside internal locks. Listener failures are logged without failing bean creation. There are no prototype or destruction events. Closing a subscription drops queued notifications; a callback already running may finish.

## Source-specific defaults

A host can provide dependencies according to the consuming bean's source. This is useful when several modules share a container but need separate loggers or host contexts.

For example, using SLF4J:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

var builder = Weft.builder()
        .sourceDefaults(Logger.class, source -> LoggerFactory.getLogger(source.id()));

// Add each module's ScanSource or explicit definitions before starting.
```

After sources are collected, the factory runs once per source and contract. A bean from source `billing` can request `Logger` without a qualifier and receive that source's logger. The mechanism works with any configured contract.

Override one source with a ready object:

```java
builder.sourceDefault("billing", Logger.class, LoggerFactory.getLogger("Billing"));
```

Selection priority is an explicit name or target key, a per-source rule, a general source-default factory, then same-source candidates with global fallback. Primary selection runs within the selected candidate group. An explicit default or generated default remains stronger than a local bean; remove that default if ordinary local selection should apply. Defaults apply to single-target dependencies, including optional, provider, and lazy requests. Lists and sets still collect every matching candidate.

The object overload registers a borrowed singleton named after the source ID. It is already initialized and is not closed by Weft. To point at an existing definition while retaining its scope and ownership, use `sourceDefault(sourceId, typeRef, targetBeanKey)` instead.

Top-level `get(Logger.class)` has no consumer source, so it can remain ambiguous. For objects created or injected on behalf of a host module, pass that context explicitly:

```java
var handler = runtime.create(RequestHandler.class, "billing");
runtime.inject(externalListener, "billing");
```

Source IDs come from `BeanSource`, not package names. Helpers such as `register(Class)` use `programmatic`. Source factories expand only during bootstrap; an unknown source passed to `create` or `inject` does not create new defaults.

## Extensions and diagnostics

The builder accepts three extension mechanisms:

| Extension | Purpose |
| --- | --- |
| `contributor(DefinitionContributor)` | Add definitions before the registry freezes. |
| `processor(BeanProcessor)` | Transform initialized objects in registration order. Each result must match the declared contract. |
| `sourceDefaults(Class<T>, factory)` | Supply a ready, borrowed dependency per discovered source. |

Processors run after `@PostConstruct`, including for externally supplied instances that otherwise skip initialization. A wrapper is exposed to consumers; destruction targets the original object.

`runtime.report()` returns the static initialization plan, including bean plans and creation order. The exception hierarchy under `api.exception` distinguishes definition, missing-bean, ambiguity, property-binding, creation, scanning, and shutdown failures. Treat error messages as diagnostics rather than a stable parsing format.

## Runtime boundaries

Each `Weft.builder().start()` creates an independent runtime. Prefer passing that runtime to your host integration so its lifetime is explicit.

There is also a process-wide facade:

```java
Weft.initialization(builder.build());
try {
    var service = Weft.get(MyService.class);
} finally {
    Weft.close();
}
```

The facade is available after startup completes. A failed startup permits a retry; successful initialization cannot be repeated, and closing the global facade prevents a later restart. Use independent runtimes when you need multiple lifetimes.

Lookup, providers, creation, and external injection support concurrent calls. Singleton creation shares one result or retained failure. Factories, processors, and callbacks remain responsible for the thread safety of their own state.

`close()` rejects new external operations and waits for accepted container operations. It does not track application business calls on objects already returned by `get`. Stop incoming work and wait for application tasks before closing. Closing from inside an active creation or callback is rejected to avoid waiting on the same operation. Repeated runtime closure is idempotent.

Weft startup describes container readiness. A host's network services, platform configuration, and other external systems have their own readiness requirements.
