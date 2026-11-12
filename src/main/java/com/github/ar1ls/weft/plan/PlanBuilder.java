package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.discovery.LifecycleMetadataReader;
import com.github.ar1ls.weft.properties.PropertiesBinder;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.spi.PropertySource;

import java.util.*;

public class PlanBuilder {

    public InitializationPlan build(DependencyResolver resolver) {
        return this.build(resolver, new PropertyResolver(Map.of()));
    }

    public InitializationPlan build(DependencyResolver resolver, PropertySource properties) {
        var plans = new LinkedHashMap<BeanKey, CreationPlan>();
        var visiting = new LinkedHashSet<BeanKey>();
        var path = new ArrayList<BeanKey>();
        for (var definition : resolver.definitions()) {
            this.visit(definition, resolver, properties, plans, visiting, path);
        }
        return new InitializationPlan(plans, new ArrayList<>(plans.keySet()));
    }

    private void visit(
            BeanDefinition definition,
            DependencyResolver resolver,
            PropertySource properties,
            Map<BeanKey, CreationPlan> plans,
            Set<BeanKey> visiting,
            List<BeanKey> path
    ) {
        if (plans.containsKey(definition.key())) {
            return;
        }
        if (!visiting.add(definition.key())) {
            throw new DefinitionException("Circular bean dependency: " + this.describePath(path, definition.key().localId())
                    + " at " + definition.key());
        }

        path.add(definition.key());
        try {
            var type = (Class<?>) definition.type().type();
            var bindings = PropertiesBinder.plan(type, properties);
            var fields = InjectionMetadataReader.readFields(type);
            var postConstruct = LifecycleMetadataReader.readPostConstruct(type);
            var preDestroy = LifecycleMetadataReader.readPreDestroy(type);
            var dependencies = new ArrayList<>(definition.factory().dependencies());
            var argumentCount = dependencies.size();
            for (var field : fields) {
                dependencies.add(field.dependency());
            }
            var resolved = new ArrayList<ResolvedDependency>();
            for (var dependency : dependencies) {
                List<BeanDefinition> targets;
                try {
                    targets = resolver.resolve(dependency);
                } catch (MissingBeanException e) {
                    throw new MissingBeanException("Missing dependency: " + this.describePath(path, dependency.type().toString())
                            + " at " + dependency.injectionPoint().description() + " for bean: " + definition.key(), e);
                } catch (AmbiguousBeanException e) {
                    throw new AmbiguousBeanException("Ambiguous dependency: " + this.describePath(path, dependency.type().toString())
                            + " for bean: " + definition.key() + "; " + e.getMessage(), e);
                }
                var keys = new ArrayList<BeanKey>();
                for (var target : targets) {
                    keys.add(target.key());
                    if (dependency.kind() != DependencyKind.PROVIDER) {
                        this.visit(target, resolver, properties, plans, visiting, path);
                    }
                }
                resolved.add(new ResolvedDependency(dependency, keys));
            }
            var resolvedFields = new ArrayList<ResolvedFieldInjection>();
            for (var index = 0; index < fields.size(); index++) {
                resolvedFields.add(new ResolvedFieldInjection(fields.get(index).field(), resolved.get(argumentCount + index)));
            }
            plans.put(definition.key(), new CreationPlan(definition, resolved.subList(0, argumentCount), resolvedFields, postConstruct, preDestroy, bindings));
        } finally {
            path.removeLast();
            visiting.remove(definition.key());
        }
    }

    private String describePath(List<BeanKey> path, String target) {
        var result = new StringJoiner(" -> ");
        for (var key : path) {
            result.add(key.localId());
        }
        result.add(target);
        return result.toString();
    }

}
