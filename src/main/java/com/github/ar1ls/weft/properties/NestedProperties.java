package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

@UtilityClass
public class NestedProperties {

    private static final ThreadLocal<Set<Class<?>>> VISITING = ThreadLocal.withInitial(HashSet::new);

    public PreparedProperty plan(Type context, String prefix, PropertySource source) {
        var type = Types.raw(context);
        if (!VISITING.get().add(type)) {
            throw new PropertyBindingException("Recursive property type: " + type.getName());
        }
        try {
            if (!InjectionMetadataReader.readFields(context).isEmpty()) {
                throw new PropertyBindingException("Nested property values cannot contain injected services: " + type.getName());
            }
            var constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new PropertyBindingException("Cannot access property type: " + type.getName());
            }
            var binding = PropertiesBinder.plan(context, source, prefix);
            return new PreparedProperty(() -> {
                try {
                    var instance = constructor.newInstance();
                    PropertiesBinder.bind(binding, instance);
                    return instance;
                } catch (ReflectiveOperationException e) {
                    throw new PropertyBindingException("Cannot construct nested properties: " + type.getName());
                }
            });
        } catch (NoSuchMethodException e) {
            throw new PropertyBindingException("Nested properties require a no-arg constructor: " + type.getName());
        } finally {
            VISITING.get().remove(type);
            if (VISITING.get().isEmpty()) {
                VISITING.remove();
            }
        }
    }

}
