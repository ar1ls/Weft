package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.InjectionPoint;
import com.github.ar1ls.weft.definition.TypeRef;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@UtilityClass
public class InjectionMetadataReader {

    public List<DependencyDescriptor> readParameters(@NonNull Executable executable) {
        var dependencies = new ArrayList<DependencyDescriptor>();
        var parameters = executable.getParameters();
        for (var index = 0; index < parameters.length; index++) {
            var parameter = parameters[index];
            var point = new InjectionPoint(executable.toGenericString() + " parameter " + index);
            var named = parameter.getAnnotation(Named.class);
            dependencies.add(readDependency(parameter.getType(), parameter.getParameterizedType(), named, point));
        }
        return List.copyOf(dependencies);
    }

    public List<FieldInjection> readFields(@NonNull Class<?> type) {
        var fields = new ArrayList<FieldInjection>();
        for (var owner : hierarchy(type)) {
            var declared = owner.getDeclaredFields();
            Arrays.sort(declared, Comparator.comparing(Field::getName));
            for (var field : declared) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    throw new DefinitionException("Injected field must be nonstatic and nonfinal: " + field.toGenericString());
                }
                if (!field.trySetAccessible()) {
                    throw new DefinitionException("Cannot access injected field: " + field.toGenericString());
                }
                var point = new InjectionPoint(field.toGenericString());
                var dependency = readDependency(field.getType(), field.getGenericType(), field.getAnnotation(Named.class), point);
                fields.add(new FieldInjection(field, dependency));
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
        if (rawType == Provider.class) {
            if (!(type instanceof ParameterizedType providerType)
                    || !(providerType.getActualTypeArguments()[0] instanceof Class<?>)) {
                throw new DefinitionException("Provider requires a concrete class argument at " + point.description());
            }
            type = providerType.getActualTypeArguments()[0];
            kind = DependencyKind.PROVIDER;
        }
        var name = named == null ? "" : named.value();
        return new DependencyDescriptor(TypeRef.of(type), name, kind, point);
    }

}
