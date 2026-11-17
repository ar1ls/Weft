package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.SourceDefault;
import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.discovery.ConditionalExclusions.ExcludedBean;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@UtilityClass
public final class ConditionalDependencyPruner {

    public Result prune(Collection<BeanDefinition> definitions,
                        List<SourceDefault> sourceDefaults,
                        Collection<ExcludedBean> exclusions) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(sourceDefaults, "sourceDefaults");
        Objects.requireNonNull(exclusions, "exclusions");

        var active = new LinkedHashMap<BeanKey, BeanDefinition>();
        for (var definition : definitions) {
            active.put(definition.key(), definition);
        }
        if (exclusions.isEmpty()) {
            return new Result(List.copyOf(active.values()), List.copyOf(sourceDefaults));
        }

        var skipped = new LinkedHashMap<BeanKey, ExcludedBean>();
        for (var exclusion : exclusions) {
            skipped.putIfAbsent(exclusion.key(), exclusion);
        }

        var dependencies = new LinkedHashMap<BeanKey, List<DependencyDescriptor>>();
        for (var definition : active.values()) {
            var declared = new ArrayList<>(definition.factory().dependencies());
            if (definition.factory().initialize()) {
                for (var field : InjectionMetadataReader.readFields(definition.type().type())) {
                    declared.add(field.dependency());
                }
            }
            dependencies.put(definition.key(), List.copyOf(declared));
        }

        while (true) {
            var eligibleDefaults = availableDefaults(sourceDefaults, skipped);
            var resolver = new DependencyResolver(active.values(), eligibleDefaults);
            var toSkip = new LinkedHashMap<BeanKey, SkipReason>();

            for (var definition : active.values()) {
                for (var dependency : dependencies.get(definition.key())) {
                    if (!required(dependency)) {
                        continue;
                    }

                    var excluded = excludedDefault(dependency, definition.source().id(),
                            sourceDefaults, skipped);
                    if (excluded == null) {
                        try {
                            resolver.resolve(dependency, definition.source().id());
                            continue;
                        } catch (MissingBeanException missing) {
                            excluded = matchingExcluded(dependency, definition.source().id(), skipped);
                        } catch (AmbiguousBeanException ambiguous) {
                            continue;
                        }
                    }
                    if (excluded != null) {
                        toSkip.put(definition.key(), new SkipReason(dependency, excluded));
                        break;
                    }
                }
            }
            if (toSkip.isEmpty()) {
                break;
            }
            for (var entry : toSkip.entrySet()) {
                var key = entry.getKey();
                var reason = entry.getValue();
                var definition = active.remove(key);
                var description = "required " + reason.dependency().type() + " at "
                        + reason.dependency().injectionPoint().description()
                        + " depends on excluded " + reason.excluded().key()
                        + " (" + reason.excluded().reason() + ")";
                skipped.put(key, new ExcludedBean(key, definition.type(), definition.name(), description));
                log.warn("SKIPPED bean {}: {}", key, description);
            }
        }
        return new Result(List.copyOf(active.values()), availableDefaults(sourceDefaults, skipped));
    }

    private boolean required(DependencyDescriptor dependency) {
        return dependency.kind() == DependencyKind.DIRECT
                || dependency.kind() == DependencyKind.PROVIDER
                || dependency.kind() == DependencyKind.LAZY;
    }

    private List<SourceDefault> availableDefaults(List<SourceDefault> defaults,
                                                  Map<BeanKey, ExcludedBean> skipped) {
        var result = new ArrayList<SourceDefault>();
        for (var binding : defaults) {
            if (!skipped.containsKey(binding.target())) {
                result.add(binding);
            }
        }
        return List.copyOf(result);
    }

    private ExcludedBean excludedDefault(DependencyDescriptor dependency,
                                         String sourceId,
                                         List<SourceDefault> defaults,
                                         Map<BeanKey, ExcludedBean> skipped) {
        if (!dependency.name().isEmpty() || dependency.targetKey() != null) {
            return null;
        }
        for (var binding : defaults) {
            if (binding.sourceId().equals(sourceId) && binding.type().equals(dependency.type())) {
                var excluded = skipped.get(binding.target());
                if (excluded != null && matches(dependency, excluded)) {
                    return excluded;
                }
            }
        }
        return null;
    }

    private ExcludedBean matchingExcluded(DependencyDescriptor dependency,
                                          String consumerSourceId,
                                          Map<BeanKey, ExcludedBean> skipped) {
        ExcludedBean external = null;
        for (var excluded : skipped.values()) {
            if (!matches(dependency, excluded)) {
                continue;
            }
            if (excluded.key().sourceId().equals(consumerSourceId)) {
                return excluded;
            }
            if (external == null) {
                external = excluded;
            }
        }
        return external;
    }

    private boolean matches(DependencyDescriptor dependency, ExcludedBean excluded) {
        if (dependency.targetKey() != null && !dependency.targetKey().equals(excluded.key())) {
            return false;
        }
        return (dependency.name().isEmpty() || dependency.name().equals(excluded.name()))
                && Types.matches(dependency.type().type(), excluded.type().type());
    }

    public record Result(List<BeanDefinition> definitions, List<SourceDefault> defaults) {

        public Result {
            definitions = List.copyOf(definitions);
            defaults = List.copyOf(defaults);
        }

    }

    private record SkipReason(DependencyDescriptor dependency, ExcludedBean excluded) {
    }

}
