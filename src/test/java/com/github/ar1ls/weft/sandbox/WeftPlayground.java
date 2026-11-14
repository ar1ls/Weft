package com.github.ar1ls.weft.sandbox;

import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.discovery.BeanScanner;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import com.github.ar1ls.weft.sandbox.beans.GreetingService;
import com.github.ar1ls.weft.sandbox.beans.DeploymentMessage;
import com.github.ar1ls.weft.sandbox.beans.GreetingProperties;
import com.github.ar1ls.weft.sandbox.beans.Formatter;
import com.github.ar1ls.weft.sandbox.beans.FormatterCatalog;
import com.github.ar1ls.weft.sandbox.beans.LifecycleProbe;
import com.github.ar1ls.weft.sandbox.beans.MessageFormatter;
import com.github.ar1ls.weft.sandbox.beans.OperationService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WeftPlayground {

    public static void main(String[] args) throws Exception {
        var scanner = new BeanScanner();
        var source = new BeanSource("sandbox", WeftPlayground.class.getClassLoader());
        var root = Path.of(WeftPlayground.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var beansPackage = WeftPlayground.class.getPackageName() + ".beans";
        var found = scanner.scan(
                root,
                source.classLoader(),
                beansPackage,
                beansPackage + ".excluded"
        );

        var registry = new BeanRegistry();
        var properties = new PropertyResolver(Map.of("greeting.text", "Welcome, ", "greeting.retries", "5", "app.mode", "dev"));
        var reader = new AnnotationDefinitionReader(properties);
        for (var clazz : found) {
            for (var definition : reader.read(clazz, source)) {
                registry.register(definition);
            }
        }

        for (var definition : registry.freeze().values()) {
            System.out.println("Registered bean: " + definition.key().localId());
        }

        try (var runtime = new WeftRuntime(registry, properties, List.of(new DeploymentMessageProcessor()))) {
            runtime.start();

            System.out.println("Lifecycle business call: " + runtime.get(LifecycleProbe.class).getMessage());

            var first = runtime.get(GreetingService.class);
            var second = runtime.get(GreetingService.class);
            System.out.println(first.greet("Alex"));
            var greeting = runtime.get(GreetingProperties.class);
            System.out.println("Properties: retries=" + greeting.getRetries() + ", timeout=" + greeting.getTimeout());
            System.out.println("Deployment: " + runtime.get(DeploymentMessage.class).text());
            System.out.println("Same singleton: " + (first == second));
            System.out.println("Same injected formatter: " + (first.getFormatter() == runtime.get(MessageFormatter.class)));

            var catalog = runtime.get(FormatterCatalog.class);
            System.out.println("Primary / named: " + runtime.get(Formatter.class).format("Alex") + " / " + catalog.getPlain().format("Alex"));
            System.out.println("All formatters: " + catalog.getAll().size());
            System.out.println("Stable set order: " + catalog.getAll().equals(new ArrayList<>(catalog.getUnique())));
            System.out.println("Optional empty: " + catalog.getMissing().isEmpty());

            var operations = runtime.get(OperationService.class);
            var firstOperation = operations.next();
            var secondOperation = operations.next();
            System.out.println("Operation IDs: " + firstOperation.getId() + " / " + secondOperation.getId());
            System.out.println("New prototype per provider call: " + (firstOperation != secondOperation));
        }
    }

}
