# Integrating host classes and modules

[README](../README.md) | [User guide](GUIDE.md)

Weft accepts the class loader your application or platform already uses. You do not need to replace it with a Weft loader or construct a `URLClassLoader` just to use the container.
There are two separate inputs: **where to inspect class files** and **which loader resolves those classes**. A `ScanSource` supplies a directory or JAR root and package filters. Its `BeanSource` supplies a source ID and the host's `ClassLoader`.
The examples below use the concrete [ModuleHostExample](../src/test/java/com/github/ar1ls/weft/sandbox/loading/ModuleHostExample.java) and its [shared contracts](../src/test/java/com/github/ar1ls/weft/sandbox/loading/api). They belong to the test source set and use that classpath. All module roots below are compiled class directories or JARs, not Java source directories.
## 1. Use the application's existing class loader

The example has two top-level beans, [BillingAction](../src/test/java/com/github/ar1ls/weft/sandbox/loading/billing/BillingAction.java) and [ReportAction](../src/test/java/com/github/ar1ls/weft/sandbox/loading/reports/ReportAction.java). Both implement `ModuleAction` and accept a `ModuleContext` in their constructor.

`ModuleHostExample.main` obtains each module's root and loader from an already-loaded anchor class:
```java
var modules = List.of(
        ModuleHostExample.fromAnchor("billing", BillingAction.class),
        ModuleHostExample.fromAnchor("reports", ReportAction.class));

try (var runtime = ModuleHostExample.start(modules, Path.of("module-data"))) {
    runtime.get(ModuleHostExample.ActionCatalog.class).print();
}
```
In an ordinary IDE deployment the anchors refer to a compiled classes directory. When packaged in an ordinary JAR, their `CodeSource` refers to that JAR. `fromAnchor` scans only the anchor's package and its subpackages, not every class in that location.

In this example both modules can share the same root and loader while retaining separate source IDs and package filters. The host registers `ActionCatalog` explicitly, so it can be a nested class; the scanned module beans must be top-level classes.
The catalog receives both module actions through `List<ModuleAction>`. Each action receives a different context: billing resolves paths beneath `module-data/billing`, and reports beneath `module-data/reports`. The example prints these paths without creating files or directories.

The helper requires a file-based `CodeSource`. If a platform hides it or uses a custom URL scheme, supply the actual directory or JAR path through `ModuleInput` instead.
## 2. Pass a loader supplied by a plugin or mod platform

Suppose a platform has already loaded a billing module and exposes its compiled root and loader. Pass those objects through unchanged:

```java
import com.github.ar1ls.weft.sandbox.loading.ModuleHostExample;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import java.nio.file.Path;
import java.util.List;
public class PlatformIntegration {
    public static WeftRuntime startBilling(Path moduleRoot, ClassLoader platformLoader,
                                          Path dataDirectory) {
        var billing = new ModuleHostExample.ModuleInput(
                "billing", moduleRoot, platformLoader,
                List.of("com.github.ar1ls.weft.sandbox.loading.billing"), List.of());
        return ModuleHostExample.start(List.of(billing), dataDirectory);
    }
}
```
This method returns an owned runtime to the platform integration. Close it when that integration stops. The platform retains ownership of its loader; Weft neither closes nor modifies it.
The root tells Weft where to read metadata. It does not add the location to `platformLoader`, change its delegation rules, or guarantee that a class came from that root. The loader must already resolve the discovered names and their dependencies. With parent-first delegation, it can resolve a parent's class of the same name.
## 3. Scan an external JAR with that same loader

No separate JAR API is required. Pass the JAR path as the root:
```java
public static WeftRuntime startBillingJar(Path billingJar, ClassLoader platformLoader,
                                         Path dataDirectory) {
    var billing = new ModuleHostExample.ModuleInput(
            "billing", billingJar, platformLoader,
            List.of("com.github.ar1ls.weft.sandbox.loading.billing"), List.of());
    return ModuleHostExample.start(List.of(billing), dataDirectory);
}
```
This is an alternative method for `PlatformIntegration` above, using the same imports. The JAR must contain compiled `BillingAction` bytecode, and `platformLoader` must already be able to resolve it. Merely pointing at a JAR does not make its classes visible to a loader.
The repository also contains [JarScanPlayground](../src/test/java/com/github/ar1ls/weft/sandbox/JarScanPlayground.java). It creates a temporary JAR using an existing bytecode fixture, supplies a loader, starts Weft through the public builder, and retrieves a bean loaded from that JAR. Its resource order closes the runtime before the loader and then removes the temporary files.
## 4. Combine several JARs or class loaders

A host can supply one loader per module or reuse the same loader across roots:
```java
public static WeftRuntime startModules(Path billingJar, ClassLoader billingLoader,
                                      Path reportsJar, ClassLoader reportsLoader,
                                      Path dataDirectory) {
    var modules = List.of(
            new ModuleHostExample.ModuleInput("billing", billingJar, billingLoader,
                    List.of("com.github.ar1ls.weft.sandbox.loading.billing"), List.of()),
            new ModuleHostExample.ModuleInput("reports", reportsJar, reportsLoader,
                    List.of("com.github.ar1ls.weft.sandbox.loading.reports"), List.of()));
    return ModuleHostExample.start(modules, dataDirectory);
}
```
This is another method for `PlatformIntegration`. Both loaders must resolve the same host-owned `ModuleAction`, `ModuleContext`, and Weft API classes. Keep those contracts visible through a shared parent or the platform's shared dependency mechanism. Packaging private copies into each module can produce distinct Java types with identical names; source IDs cannot make them assignable.
`ActionCatalog` collects actions from both sources because they implement the same shared contract. Other dependencies may cross sources too if ordinary Java type visibility and candidate selection allow it. Source IDs are dependency-resolution context, not a security boundary or an automatic class loader bridge.
One source ID can have only one scan root and one loader. For two JAR roots, use two source IDs even if they share a loader. Repeated scans of the same root and loader may reuse an ID to combine package filters. Scanning the same classes under different IDs registers separate definitions; it does not deduplicate them across sources.
## 5. Supply a dependency for each module

`ModuleHostExample.start` registers a default factory:

```java
.sourceDefaults(ModuleContext.class,
        source -> new ModuleContext(source.id(), baseDirectory.resolve(source.id())))
```
The factory expands during bootstrap for each discovered source. `BillingAction` asks for `ModuleContext` without a name and gets billing's context. `ReportAction` gets reports' context. Source IDs in this example are host-controlled identifiers, not unvalidated filesystem path input.
The host's explicitly registered catalog has source ID `programmatic`, so it also gets a context definition even though it does not consume it. This reflects the actual rule: the factory expands for every discovered source, not only sources with matching injection points.

The resulting context objects are ready, borrowed singletons. They are not injected, initialized, or closed again by Weft. To override a particular module, add this before starting the builder:
```java
builder.sourceDefault("billing", ModuleContext.class,
        new ModuleContext("billing", Path.of("dedicated-billing-data")));
```

The explicit rule suppresses the general factory for that source and contract. A direct `runtime.get(ModuleContext.class)` has no consumer source and is ambiguous when multiple contexts are present. Use a consumer with source context or an explicit name such as `runtime.get(ModuleContext.class, "billing")`.
## 6. Create a task or inject an object owned by the platform

`ModuleHostExample` includes two complete consumers of the same context:

```java
var job = runtime.create(ModuleHostExample.ExportJob.class, "billing");
System.out.println(job.destination());

var listener = new ModuleHostExample.HostListener();
runtime.inject(listener, "reports");
System.out.println(listener.destination());
```
`ExportJob` gets billing's context through its constructor. It is not added to the registry or retained for runtime shutdown. The caller owns it after successful creation.

`HostListener` already exists, so Weft fills only its `@Inject` field with reports' context. It does not bind properties or call lifecycle methods on that external object. This fits objects constructed by another framework, such as commands, listeners, and request handlers.
The source ID selects dependency defaults and otherwise prefers matching unnamed single-target dependencies from that source before falling back to all sources. Explicit names and collection injection keep their global behavior. It does not load `ExportJob` through the billing loader or move the object into that loader. These calls use an already-resolved Java class or existing instance. Unknown source IDs do not create new defaults after startup.
## 7. Let a host create its own loader when needed

If your application owns plugin loading rather than receiving a platform loader, it can construct a `URLClassLoader` and pass it to the same integration. For example, in a host method with `Path billingJar` and `Path dataDirectory` parameters:
```java
try (var loader = new java.net.URLClassLoader(
        new java.net.URL[]{billingJar.toUri().toURL()},
        ModuleHostExample.class.getClassLoader())) {
    try (var runtime = PlatformIntegration.startBillingJar(billingJar, loader, dataDirectory)) {
        runtime.get(ModuleHostExample.ActionCatalog.class).print();
    }
}
```
Keep the shared API on the parent's classpath and the module implementation only in the module JAR if you need to demonstrate loading from the child. If `BillingAction` is already visible in the parent, standard `URLClassLoader` delegation will use that class instead. The directory example intentionally uses parent-visible implementations; the generated implementation in `JarScanPlayground` is absent from its parent.
Close the runtime before a host-owned loader. If startup throws, the outer resource block still closes that loader. Closing a loader releases its open resources; it does not guarantee class unloading. Stop module work and release application references separately. Weft does not provide live registration, hot reload, or automatic module unloading.
## Conditional discovery in embedded hosts

Hosts such as NeoForge and Velocity often already know which physical JAR belongs to a mod or plugin. Prefer passing that root, its unique source ID, and its existing platform loader into the **built-in** Weft discovery:

```java
// modJar, modId, and platformLoader are supplied by the host.
var source = new BeanSource(modId, platformLoader);
builder.scan(new ScanSource(modJar, source, List.of()));
```

A platform may use annotation metadata as an inexpensive prefilter to avoid scanning unrelated JARs. However, the final conditional evaluation must run in Weft's `WeftBootstrap` in order to record excluded beans and propagate dependent `SKIPPED` correctly.

Do **not** manually call `AnnotationDefinitionReader.read(...)` and pass only the returned definitions to `builder.register(...)` if transitive condition-based pruning is required. A false registration condition means no definition is returned, and the external host loses the information needed to distinguish an intentionally excluded dependency from a genuinely unregistered dependency. See [Conditional registration and dependent-bean pruning](CONDITIONS.md).

Scan roots still must be actual directories or ordinary/multi-release JARs visible on disk. The platform loader must resolve all discovered classes. Keep one root and loader per source ID, avoid duplicate mod versions, and handle multi-mod JARs as an explicit host policy.

## Discovery boundaries
| Case | What the current implementation does |
| --- | --- |
| Existing application/platform loader | Accepts it through `BeanSource`; keeps its ownership with the host. |
| Directory root | Reads `.class` files beneath that root and filters class names by package. |
| Ordinary or multi-release JAR | Reads class entries; multi-release selection follows the running Java version. |
| Empty package list | Inspects the whole root. |
| Excluded packages | Excludes those packages and their subpackages for that scan entry. An overlapping entry can still include them. |
| Overlapping scans of one source | Deduplicates class names within that source. |
| Nested classes, interfaces, abstract classes | Does not register them through scanning. Public concrete nested classes can still be registered explicitly. |
| Nested/fat JAR | No nested-archive traversal or loader-specific extraction. Supply a supported root and a suitable loader. |
| Loader without an explicit root | No whole-loader or whole-classpath enumeration. |
| Host already has `Class<?>` objects | Can use explicit definitions instead of file scanning, but plain registration does not preserve conditional-exclusion metadata for dependent `SKIPPED`. |
For an already-loaded concrete class, `builder.register(type, sourceId)` creates a constructor-based singleton with an explicit host source ID; `builder.register(type)` retains source `programmatic`. Neither call interprets the class-level registration conditions. An external `AnnotationDefinitionReader.read(type, new BeanSource(id, loader))` can interpret annotations, but its returned definitions do **not** contain the excluded-bean ledger needed for transitive `SKIPPED`.
When you need conditional dependency pruning, prefer passing a supported root through `ScanSource` and let Weft process the annotation discovery itself. If you intentionally use external reading, supply a `PropertyResolver` consistent with the builder properties and understand that a missing bean excluded during that external pass will not automatically cause dependent pruning.
