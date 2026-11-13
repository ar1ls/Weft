package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import lombok.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BeanCreator {

    public Object create(BeanDefinition definition) {
        return this.create(definition, new Object[0]);
    }

    public Object create(@NonNull BeanDefinition definition, @NonNull Object[] arguments) {
        try {
            Types.validate(definition.type().type());
        } catch (DefinitionException e) {
            throw new BeanCreationException("Invalid bean type: " + definition.key(), e);
        }
        var type = Types.raw(definition.type().type());
        var dependencies = definition.factory().dependencies();
        if (arguments.length != dependencies.size()) {
            throw new BeanCreationException("Expected " + dependencies.size() + " factory arguments: " + definition.key());
        }
        for (var index = 0; index < arguments.length; index++) {
            var dependency = dependencies.get(index);
            if (!this.isValidArgument(dependency, arguments[index])) {
                throw new BeanCreationException("Invalid argument at " + dependency.injectionPoint().description()
                        + " for bean: " + definition.key());
            }
        }

        Object bean;
        try {
            bean = definition.factory().create(arguments);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("Factory failed for bean: " + definition.key(), e.getCause());
        } catch (Exception e) {
            throw new BeanCreationException("Cannot create bean: " + definition.key(), e);
        }

        if (!type.isInstance(bean)) {
            throw new BeanCreationException("Invalid factory result for bean: " + definition.key());
        }
        return bean;
    }

    private boolean isValidArgument(DependencyDescriptor dependency, Object argument) {
        var type = Types.raw(dependency.type().type());
        return switch (dependency.kind()) {
            case DIRECT, LAZY -> type.isInstance(argument);
            case VALUE -> argument == null ? !type.isPrimitive() : type.isPrimitive() || type.isInstance(argument);
            case PROVIDER -> argument instanceof Provider<?>;
            case OPTIONAL ->
                    argument instanceof Optional<?> optional && (optional.isEmpty() || type.isInstance(optional.get()));
            case LIST -> argument instanceof List<?> list && this.containsOnly(type, list);
            case SET -> argument instanceof Set<?> set && this.containsOnly(type, set);
        };
    }

    private boolean containsOnly(Class<?> type, Collection<?> values) {
        for (var value : values) {
            if (!type.isInstance(value)) {
                return false;
            }
        }
        return true;
    }

    public <T> T create(Class<T> type) {
        try {
            type.getConstructor();
            var factory = new ConstructorBeanFactory(type);
            if (!factory.dependencies().isEmpty()) {
                throw new BeanCreationException("Resolved constructor arguments required: " + type.getName());
            }
            return type.cast(factory.create(new Object[0]));
        } catch (NoSuchMethodException e) {
            throw new BeanCreationException("Public no-arg constructor required: " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("Constructor failed: " + type.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException("Cannot instantiate: " + type.getName(), e);
        } catch (DefinitionException e) {
            throw new BeanCreationException("Cannot select constructor: " + type.getName(), e);
        }
    }

}
