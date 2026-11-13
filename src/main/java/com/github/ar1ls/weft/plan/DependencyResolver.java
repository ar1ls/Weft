package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DependencyResolver {

    @Getter
    @Accessors(fluent = true)
    Collection<BeanDefinition> definitions;

    public DependencyResolver(@NonNull Collection<BeanDefinition> definitions) {
        var byKey = new LinkedHashMap<BeanKey, BeanDefinition>();
        for (var definition : definitions) {
            Types.validate(definition.type().type());
            var previous = byKey.putIfAbsent(definition.key(), definition);
            if (previous != null) {
                throw new DefinitionException("Duplicate bean key: " + definition.key());
            }
        }
        this.definitions = List.copyOf(byKey.values());
    }

    public BeanDefinition resolve(@NonNull Class<?> type) {
        return this.resolve(type, "");
    }

    public BeanDefinition resolve(@NonNull Class<?> type, @NonNull String name) {
        var dependency = new DependencyDescriptor(TypeRef.of(type), name, DependencyKind.DIRECT,
                new InjectionPoint("get(" + type.getName() + ")"));
        return this.resolve(dependency).getFirst();
    }

    public List<BeanDefinition> resolve(@NonNull DependencyDescriptor dependency) {
        var point = dependency.injectionPoint().description();
        Types.validate(dependency.type().type());
        var candidates = new ArrayList<BeanDefinition>();
        for (var definition : this.definitions) {
            if (dependency.targetKey() != null && !dependency.targetKey().equals(definition.key())) {
                continue;
            }
            if (Types.matches(dependency.type().type(), definition.type().type())
                    && (dependency.name().isEmpty() || dependency.name().equals(definition.name()))) {
                candidates.add(definition);
            }
        }
        candidates.sort(Comparator.comparing((BeanDefinition definition) -> definition.key().sourceId())
                .thenComparing(definition -> definition.key().localId()));
        if (dependency.kind() == DependencyKind.LIST || dependency.kind() == DependencyKind.SET) {
            return List.copyOf(candidates);
        }
        if (candidates.isEmpty()) {
            if (dependency.kind() == DependencyKind.OPTIONAL) {
                return List.of();
            }
            var key = dependency.targetKey() == null ? "" : " and key " + dependency.targetKey();
            throw new MissingBeanException("No bean registered for type: " + dependency.type()
                    + " with name '" + dependency.name() + "'" + key + " at " + point);
        }
        if (candidates.size() == 1) {
            return List.of(candidates.getFirst());
        }
        BeanDefinition primary = null;
        var primaryCount = 0;
        for (var candidate : candidates) {
            if (candidate.primary()) {
                primary = candidate;
                primaryCount++;
            }
        }
        if (primaryCount == 1) {
            return List.of(primary);
        }
        var keys = new StringJoiner(", ");
        for (var candidate : candidates) {
            keys.add(candidate.key() + (candidate.primary() ? " [primary]" : ""));
        }
        throw new AmbiguousBeanException("Multiple beans for type " + dependency.type()
                + " with name '" + dependency.name() + "' at " + point + ": " + keys);
    }

}
