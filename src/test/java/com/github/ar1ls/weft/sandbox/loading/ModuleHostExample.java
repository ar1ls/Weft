package com.github.ar1ls.weft.sandbox.loading;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import com.github.ar1ls.weft.sandbox.loading.api.ModuleAction;
import com.github.ar1ls.weft.sandbox.loading.api.ModuleContext;
import com.github.ar1ls.weft.sandbox.loading.billing.BillingAction;
import com.github.ar1ls.weft.sandbox.loading.reports.ReportAction;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

/** Examples of host-supplied roots, class loaders, and module dependency contexts. */
public class ModuleHostExample {
    /** The host supplies both the discovery location and the loader that can load its classes. */
    public record ModuleInput(String id, Path root, ClassLoader loader,
                              List<String> packages, List<String> excludedPackages) {
    }

    /** Does not create or close module class loaders, or create directories on disk. */
    public static WeftRuntime start(List<ModuleInput> modules, Path dataDirectory) {
        var baseDirectory = dataDirectory.toAbsolutePath().normalize();
        var builder = Weft.builder()
                .register(ActionCatalog.class)
                .sourceDefaults(ModuleContext.class,
                        source -> new ModuleContext(source.id(), baseDirectory.resolve(source.id())));

        for (var module : modules) {
            builder.scan(new ScanSource(module.root(), new BeanSource(module.id(), module.loader()),
                    module.packages(), module.excludedPackages()));
        }
        return builder.start();
    }

    /** Suitable for ordinary directory/JAR deployments with a file-based CodeSource. */
    public static ModuleInput fromAnchor(String id, Class<?> anchor) throws URISyntaxException {
        var codeSource = anchor.getProtectionDomain().getCodeSource();
        if (codeSource == null || !"file".equalsIgnoreCase(codeSource.getLocation().getProtocol())) {
            throw new IllegalArgumentException("Supply an explicit directory or JAR root for " + anchor.getName());
        }
        return new ModuleInput(id, Path.of(codeSource.getLocation().toURI()), anchor.getClassLoader(),
                List.of(anchor.getPackageName()), List.of());
    }

    public static void main(String[] args) throws Exception {
        var modules = List.of(fromAnchor("billing", BillingAction.class),
                fromAnchor("reports", ReportAction.class));

        try (var runtime = start(modules, Path.of("module-data"))) {
            runtime.get(ActionCatalog.class).print();

            // This object is created by Weft but is not registered or retained for shutdown.
            var job = runtime.create(ExportJob.class, "billing");
            System.out.println("Created job: " + job.destination());

            // A host-created object gets only its @Inject fields populated.
            var listener = new HostListener();
            runtime.inject(listener, "reports");
            System.out.println("Host listener: " + listener.destination());
        }
    }

    public static final class ActionCatalog {
        private final List<ModuleAction> actions;

        public ActionCatalog(List<ModuleAction> actions) {
            this.actions = actions;
        }

        public void print() {
            actions.forEach(action -> System.out.println(action.description()));
        }
    }

    public static final class ExportJob {
        private final ModuleContext context;

        public ExportJob(ModuleContext context) {
            this.context = context;
        }

        public Path destination() {
            return context.dataDirectory().resolve("exports");
        }
    }

    public static final class HostListener {
        @Inject
        private ModuleContext context;

        public Path destination() {
            return context.dataDirectory().resolve("events");
        }
    }
}
