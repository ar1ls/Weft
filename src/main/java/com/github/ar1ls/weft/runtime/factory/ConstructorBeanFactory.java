package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConstructorBeanFactory implements BeanFactory {

    Constructor<?> constructor;
    List<DependencyDescriptor> dependencies;

    public ConstructorBeanFactory(@NonNull Class<?> type) {
        this.constructor = this.selectConstructor(type);
        this.dependencies = InjectionMetadataReader.readParameters(this.constructor);
    }

    @Override
    public List<DependencyDescriptor> dependencies() {
        return this.dependencies;
    }

    @Override
    public Object create(@NonNull Object[] arguments) throws ReflectiveOperationException {
        if (arguments.length != this.dependencies.size()) {
            throw new IllegalArgumentException("Expected " + this.dependencies.size()
                    + " constructor arguments: " + this.constructor.toGenericString());
        }

        return this.constructor.newInstance(arguments);
    }

    private Constructor<?> selectConstructor(Class<?> type) {
        if (!Modifier.isPublic(type.getModifiers()) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new DefinitionException("Public concrete bean class required: " + type.getName());
        }

        Constructor<?> selected = null;
        for (var constructor : type.getDeclaredConstructors()) {
            if (!constructor.isAnnotationPresent(Inject.class)) {
                continue;
            }
            if (selected != null) {
                throw new DefinitionException("Multiple @Inject constructors: " + type.getName());
            }
            selected = constructor;
        }
        if (selected != null) {
            if (!Modifier.isPublic(selected.getModifiers())) {
                throw new DefinitionException("Only public @Inject constructors are supported: " + type.getName());
            }
            return selected;
        }

        var constructors = type.getConstructors();
        if (constructors.length != 1) {
            throw new DefinitionException("Expected one public constructor or one @Inject constructor: " + type.getName());
        }
        return constructors[0];
    }

}
