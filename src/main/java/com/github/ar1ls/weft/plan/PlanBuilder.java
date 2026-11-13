package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.discovery.FieldInjection;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.discovery.LifecycleMetadataReader;
import com.github.ar1ls.weft.properties.BindingPlan;
import com.github.ar1ls.weft.properties.PropertiesBinder;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.spi.PropertySource;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
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
            this.visit(definition, resolver, properties, plans, visiting, path, definition.source().id());
        }
        return new InitializationPlan(plans, new ArrayList<>(plans.keySet()));
    }

    public CreationPlan transientPlan(BeanDefinition definition, DependencyResolver resolver, PropertySource properties, InitializationPlan existing) {
        return this.transientPlan(definition, resolver, properties, existing, definition.source().id());
    }

    public CreationPlan transientPlan(BeanDefinition definition, DependencyResolver resolver, PropertySource properties,
                                     InitializationPlan existing, String consumerSourceId) {
        var plans = new LinkedHashMap<>(existing.beans());
        this.visit(definition, resolver, properties, plans, new LinkedHashSet<>(), new ArrayList<>(), consumerSourceId);
        return plans.get(definition.key());
    }

    public CreationPlan destructionPlan(CreationPlan declared, Class<?> implementation) {
        var hooks = declared.definition().factory().owned() ? LifecycleMetadataReader.readPreDestroy(implementation) : List.<Method>of();
        return new CreationPlan(declared.definition(), declared.arguments(), declared.fields(), declared.postConstruct(), hooks, declared.properties());
    }

    public CreationPlan instancePlan(CreationPlan declared, Type implementation, DependencyResolver resolver, PropertySource properties) {
        var type = Types.raw(implementation);
        var bindings = PropertiesBinder.plan(implementation, properties);
        var fields = new ArrayList<ResolvedFieldInjection>();
        for (var injection : InjectionMetadataReader.readFields(implementation)) {
            var keys = new ArrayList<BeanKey>();
            for (var target : resolver.resolve(injection.dependency(), declared.definition().source().id())) {
                keys.add(target.key());
            }
            fields.add(new ResolvedFieldInjection(injection.field(), new ResolvedDependency(injection.dependency(), keys)));
        }
        var hooks = LifecycleMetadataReader.readPostConstruct(type);
        return new CreationPlan(declared.definition(), declared.arguments(), fields, hooks, declared.preDestroy(), bindings);
    }

    private void visit(
            BeanDefinition definition,
            DependencyResolver resolver,
            PropertySource properties,
            Map<BeanKey, CreationPlan> plans,
            Set<BeanKey> visiting,
            List<BeanKey> path,
            String consumerSourceId
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
            var type = Types.raw(definition.type().type());
            var bindings = definition.factory().initialize() ? PropertiesBinder.plan(definition.type().type(), properties)
                    : new BindingPlan(List.of());
            var fields = definition.factory().initialize() ? InjectionMetadataReader.readFields(definition.type().type())
                    : List.<FieldInjection>of();
            var postConstruct = definition.factory().initialize() ? LifecycleMetadataReader.readPostConstruct(type) : List.<Method>of();
            var preDestroy = definition.factory().owned() ? LifecycleMetadataReader.readPreDestroy(type) : List.<Method>of();
            var dependencies = new ArrayList<>(definition.factory().dependencies());
            var argumentCount = dependencies.size();
            for (var field : fields) {
                dependencies.add(field.dependency());
            }
            var resolved = new ArrayList<ResolvedDependency>();
            for (var dependency : dependencies) {
                if (dependency.kind() == DependencyKind.VALUE) {
                    try {
                        resolved.add(new ResolvedDependency(dependency, List.of(), dependency.property().resolve(properties)));
                    } catch (PropertyBindingException e) {
                        throw new PropertyBindingException(e.getMessage() + " at " + dependency.injectionPoint().description()
                                + " for bean: " + definition.key());
                    }
                    continue;
                }
                List<BeanDefinition> targets;
                try {
                    targets = resolver.resolve(dependency, consumerSourceId);
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
                    if (dependency.kind() != DependencyKind.PROVIDER && dependency.kind() != DependencyKind.LAZY) {
                        this.visit(target, resolver, properties, plans, visiting, path, target.source().id());
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
