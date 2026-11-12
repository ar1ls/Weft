package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.definition.DependencyKind;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DependencyResolver {

    Map<Class<?>, BeanDefinition> definitions;

    public DependencyResolver(@NonNull Collection<BeanDefinition> definitions) {
        var byType = new LinkedHashMap<Class<?>, BeanDefinition>();
        for (var definition : definitions) {
            if (!(definition.type().type() instanceof Class<?> type)) {
                throw new DefinitionException("Only class types are supported: " + definition.key());
            }
            var previous = byType.putIfAbsent(type, definition);
            if (previous != null) {
                throw new DefinitionException("Multiple beans for type " + type.getName()
                        + ": " + previous.key() + " and " + definition.key());
            }
        }
        this.definitions = Collections.unmodifiableMap(byType);
    }

    public Collection<BeanDefinition> definitions() {
        return this.definitions.values();
    }

    public BeanDefinition resolve(@NonNull Class<?> type) {
        var definition = this.definitions.get(type);
        if (definition == null) {
            throw new MissingBeanException("No bean registered for type: " + type.getName());
        }
        return definition;
    }

    public BeanDefinition resolve(@NonNull DependencyDescriptor dependency) {
        var point = dependency.injectionPoint().description();
        if (dependency.kind() != DependencyKind.DIRECT || !dependency.name().isEmpty()) {
            throw new DefinitionException("Only unnamed direct dependencies are supported at " + point);
        }
        if (!(dependency.type().type() instanceof Class<?> type)) {
            throw new DefinitionException("Only class dependencies are supported at " + point);
        }
        return this.resolve(type);
    }

}
