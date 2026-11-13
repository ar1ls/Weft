package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.*;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.properties.PropertyRequest;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.*;
import java.util.*;

@UtilityClass
public class InjectionMetadataReader {

    public List<DependencyDescriptor> readParameters(@NonNull Executable executable) {
        return readParameters(executable, executable.getDeclaringClass());
    }

    public List<DependencyDescriptor> readParameters(@NonNull Executable executable, Type context) {
        var dependencies = new ArrayList<DependencyDescriptor>();
        var parameters = executable.getParameters();
        for (var index = 0; index < parameters.length; index++) {
            var parameter = parameters[index];
            var point = new InjectionPoint(executable.toGenericString() + " parameter " + index);
            var configuration = executable.getDeclaringClass().getAnnotation(Properties.class);
            var property = parameter.getAnnotation(Property.class);
            var env = parameter.getAnnotation(Env.class);
            var implicit = executable instanceof Constructor<?> && executable.getDeclaringClass().isRecord() && configuration != null;
            if (property != null || env != null || implicit) {
                if (parameter.isAnnotationPresent(Named.class) || parameter.isAnnotationPresent(Lazy.class)) {
                    throw new DefinitionException("Conflicting property parameter annotations at " + point.description());
                }
                var local = property == null || property.value().isEmpty() ? parameter.getName() : property.value();
                if ((property == null || property.value().isEmpty()) && env == null && !parameter.isNamePresent()) {
                    throw new DefinitionException("Explicit property name or -parameters is required at " + point.description());
                }
                var key = env != null ? env.value() : configuration == null || configuration.value().isEmpty()
                        ? local : configuration.value() + "." + local;
                var parameterType = Types.member(context, executable.getDeclaringClass(), parameter.getParameterizedType());
                dependencies.add(new DependencyDescriptor(TypeRef.of(parameterType), "", DependencyKind.VALUE, point,
                        new PropertyRequest(key, parameterType, property, env)));
                continue;
            }
            var named = parameter.getAnnotation(Named.class);
            var parameterType = Types.member(context, executable.getDeclaringClass(), parameter.getParameterizedType());
            validate(parameterType, point);
            var dependency = readDependency(Types.raw(parameterType), parameterType, named, point);
            dependencies.add(lazy(dependency, parameter.isAnnotationPresent(Lazy.class)));
        }
        return List.copyOf(dependencies);
    }

    public List<FieldInjection> readFields(@NonNull Class<?> type) {
        return readFields((Type) type);
    }

    public List<FieldInjection> readFields(@NonNull Type context) {
        var type = Types.raw(context);
        var fields = new ArrayList<FieldInjection>();
        for (var owner : hierarchy(type)) {
            var declared = owner.getDeclaredFields();
            Arrays.sort(declared, Comparator.comparing(Field::getName));
            for (var field : declared) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                if (field.isAnnotationPresent(Property.class) || field.isAnnotationPresent(Env.class)) {
                    throw new DefinitionException("Conflicting property/injection annotations at " + field.toGenericString());
                }
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    throw new DefinitionException("Injected field must be nonstatic and nonfinal: " + field.toGenericString());
                }
                if (!field.trySetAccessible()) {
                    throw new DefinitionException("Cannot access injected field: " + field.toGenericString());
                }
                var point = new InjectionPoint(field.toGenericString());
                var resolvedType = Types.member(context, owner, field.getGenericType());
                validate(resolvedType, point);
                var dependency = readDependency(Types.raw(resolvedType), resolvedType, field.getAnnotation(Named.class), point);
                fields.add(new FieldInjection(field, lazy(dependency, field.isAnnotationPresent(Lazy.class))));
            }
        }
        return List.copyOf(fields);
    }

    public List<Class<?>> hierarchy(@NonNull Class<?> type) {
        var hierarchy = new ArrayList<Class<?>>();
        for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            hierarchy.addFirst(owner);
        }
        return List.copyOf(hierarchy);
    }

    private DependencyDescriptor readDependency(Class<?> rawType, Type type, Named named, InjectionPoint point) {
        var kind = DependencyKind.DIRECT;
        if (rawType == Provider.class || rawType == Optional.class || rawType == List.class || rawType == Set.class) {
            if (!(type instanceof ParameterizedType providerType)
                    || providerType.getActualTypeArguments().length != 1) {
                throw new DefinitionException(rawType.getSimpleName() + " requires a concrete class argument at " + point.description());
            }
            type = providerType.getActualTypeArguments()[0];
            validate(type, point);
            var element = Types.raw(type);
            if (element == Provider.class || element == Optional.class || element == List.class || element == Set.class) {
                throw new DefinitionException("Nested dependency wrappers are not supported at " + point.description());
            }
            if (rawType == Provider.class) {
                kind = DependencyKind.PROVIDER;
            } else if (rawType == Optional.class) {
                kind = DependencyKind.OPTIONAL;
            } else if (rawType == List.class) {
                kind = DependencyKind.LIST;
            } else {
                kind = DependencyKind.SET;
            }
        }
        if (named != null && named.value().isBlank()) {
            throw new DefinitionException("Named must not be blank at " + point.description());
        }
        var name = named == null ? "" : named.value();
        validate(type, point);
        return new DependencyDescriptor(TypeRef.of(type), name, kind, point);
    }

    private DependencyDescriptor lazy(DependencyDescriptor dependency, boolean lazy) {
        if (!lazy) {
            return dependency;
        }
        var type = Types.raw(dependency.type().type());
        if (dependency.kind() != DependencyKind.DIRECT || !type.isInterface() || type.isSealed()) {
            throw new DefinitionException("@Lazy injection requires a nonsealed interface at " + dependency.injectionPoint());
        }
        return new DependencyDescriptor(dependency.type(), dependency.name(), DependencyKind.LAZY, dependency.injectionPoint());
    }

    private void validate(Type type, InjectionPoint point) {
        try {
            Types.validate(type);
        } catch (DefinitionException e) {
            throw new DefinitionException(e.getMessage() + " at " + point.description(), e);
        }
    }

}
