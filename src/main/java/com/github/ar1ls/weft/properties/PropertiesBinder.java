package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

@UtilityClass
public class PropertiesBinder {

    public BindingPlan plan(@NonNull Class<?> type, @NonNull PropertySource properties) {
        var configuration = findConfiguration(type);
        var prefix = configuration == null ? "" : configuration.value().strip();
        var bindings = new ArrayList<PropertyBinding>();
        for (var owner : InjectionMetadataReader.hierarchy(type)) {
            var fields = owner.getDeclaredFields();
            Arrays.sort(fields, Comparator.comparing(Field::getName));
            for (var field : fields) {
                var property = field.getAnnotation(Property.class);
                var env = field.getAnnotation(Env.class);
                var explicit = property != null || env != null;
                if (field.isSynthetic() || (!explicit && (configuration == null
                        || Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Inject.class)))) {
                    continue;
                }
                validate(field, property, env);
                var localKey = property == null || property.value().isEmpty() ? field.getName() : property.value().strip();
                var key = env == null ? (prefix.isEmpty() ? localKey : prefix + "." + localKey) : env.value().strip();
                if (localKey.isBlank() || key.isBlank()) {
                    throw new DefinitionException("Property key must not be blank at " + field.toGenericString());
                }
                DefaultValueConverter.requireSupported(key, field.getType());
                if (!field.trySetAccessible()) {
                    throw new DefinitionException("Cannot access property field: " + field.toGenericString());
                }
                var value = properties.find(key);
                if (value.isEmpty()) {
                    value = defaultValue(property, env);
                }
                if (value.isEmpty()) {
                    if (env != null && !env.required()) {
                        continue;
                    }
                    throw new PropertyBindingException("Missing property '" + key + "' at " + field.toGenericString());
                }
                Object converted;
                try {
                    converted = DefaultValueConverter.convert(key, value.get(), field.getType());
                } catch (PropertyBindingException e) {
                    throw new PropertyBindingException(e.getMessage() + " at " + field.toGenericString());
                }
                bindings.add(new PropertyBinding(field, key, converted));
            }
        }
        return new BindingPlan(bindings);
    }

    public void bind(@NonNull BindingPlan plan, @NonNull Object bean) {
        for (var binding : plan.fields()) {
            try {
                binding.field().set(bean, binding.value());
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new BeanCreationException("Cannot bind property '" + binding.key() + "' at " + binding.field().toGenericString(), e);
            }
        }
    }

    private void validate(Field field, Property property, Env env) {
        if ((property != null && env != null) || field.isAnnotationPresent(Inject.class) || field.isAnnotationPresent(Named.class)) {
            throw new DefinitionException("Conflicting property annotations at " + field.toGenericString());
        }
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            throw new DefinitionException("Property field must be nonstatic and nonfinal: " + field.toGenericString());
        }
        if (property != null && property.defaultValue().length > 1) {
            throw new DefinitionException("At most one property default is allowed at " + field.toGenericString());
        }
    }

    private Optional<String> defaultValue(Property property, Env env) {
        if (property != null && property.defaultValue().length == 1) {
            return Optional.of(property.defaultValue()[0]);
        }
        if (env != null && !env.defaultValue().isEmpty()) {
            return Optional.of(env.defaultValue());
        }
        return Optional.empty();
    }

    private Properties findConfiguration(Class<?> type) {
        for (var owner : InjectionMetadataReader.hierarchy(type).reversed()) {
            var configuration = owner.getAnnotation(Properties.class);
            if (configuration != null) {
                return configuration;
            }
        }
        return null;
    }

}
