package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.WeftOptions;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.discovery.BeanScanner;
import com.github.ar1ls.weft.properties.PropertyResolver;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

@UtilityClass
public class WeftBootstrap {

    public WeftRuntime start(@NonNull WeftOptions options) {
        var properties = new PropertyResolver(options.properties());
        var registry = new BeanRegistry();
        for (var definition : options.definitions()) {
            registry.register(definition);
        }
        for (var contributor : options.contributors()) {
            for (var definition : contributor.definitions()) {
                registry.register(definition);
            }
        }
        var sources = new LinkedHashMap<String, BeanSource>();
        for (var definition : registry.definitions().values()) {
            var previous = sources.putIfAbsent(definition.source().id(), definition.source());
            if (previous != null && previous.classLoader() != definition.source().classLoader()) {
                throw new DefinitionException("Source id has different classloaders: " + definition.source().id());
            }
        }
        var roots = new LinkedHashMap<String, Path>();
        var scanned = new HashSet<String>();
        var reader = new AnnotationDefinitionReader(properties);
        var scanner = new BeanScanner();
        for (var scan : options.sources()) {
            var previous = sources.putIfAbsent(scan.source().id(), scan.source());
            var root = roots.putIfAbsent(scan.source().id(), scan.root());
            if (previous != null && (previous.classLoader() != scan.source().classLoader() || root != null && !root.equals(scan.root()))) {
                throw new DefinitionException("Source id has different root/classloader: " + scan.source().id());
            }
            var packages = scan.packages().isEmpty() ? List.of("") : scan.packages();
            for (var packageName : packages) {
                for (var type : scanner.scan(scan.root(), scan.source().classLoader(), packageName, scan.excludedPackages().toArray(String[]::new))) {
                    if (!scanned.add(scan.source().id() + ":" + type.getName())) {
                        continue;
                    }
                    for (var definition : reader.read(type, scan.source())) {
                        registry.register(definition);
                    }
                }
            }
        }
        var runtime = new WeftRuntime(registry, properties, options.processors());
        runtime.start();
        return runtime;
    }

}
