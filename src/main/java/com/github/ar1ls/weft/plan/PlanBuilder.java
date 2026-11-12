package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.discovery.LifecycleMetadataReader;

import java.util.*;

public class PlanBuilder {

    public InitializationPlan build(DependencyResolver resolver) {
        var plans = new LinkedHashMap<BeanKey, CreationPlan>();
        var visiting = new LinkedHashSet<BeanKey>();
        var path = new ArrayList<BeanKey>();
        for (var definition : resolver.definitions()) {
            this.visit(definition, resolver, plans, visiting, path);
        }
        return new InitializationPlan(plans, new ArrayList<>(plans.keySet()));
    }

    private void visit(
            BeanDefinition definition,
            DependencyResolver resolver,
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
                BeanDefinition target;
                try {
                    target = resolver.resolve(dependency);
                } catch (MissingBeanException e) {
                    throw new MissingBeanException("Missing dependency: " + this.describePath(path, dependency.type().toString())
                            + " at " + dependency.injectionPoint().description() + " for bean: " + definition.key(), e);
                }
                resolved.add(new ResolvedDependency(dependency, target.key()));
                if (dependency.kind() == DependencyKind.DIRECT) {
                    this.visit(target, resolver, plans, visiting, path);
                }
            }
            var resolvedFields = new ArrayList<ResolvedFieldInjection>();
            for (var index = 0; index < fields.size(); index++) {
                resolvedFields.add(new ResolvedFieldInjection(fields.get(index).field(), resolved.get(argumentCount + index)));
            }
            plans.put(definition.key(), new CreationPlan(definition, resolved.subList(0, argumentCount), resolvedFields, postConstruct, preDestroy));
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
