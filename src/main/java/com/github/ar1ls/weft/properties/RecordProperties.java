package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.Property;
import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

@UtilityClass
public class RecordProperties {

    private static final ThreadLocal<Set<Class<?>>> VISITING = ThreadLocal.withInitial(HashSet::new);

    public PreparedProperty plan(Type context, String prefix, PropertySource source) {
        var type = Types.raw(context);
        if (!VISITING.get().add(type)) {
            throw new PropertyBindingException("Recursive property record: " + type.getName());
        }
        try {
            var components = type.getRecordComponents();
            var arguments = new Object[components.length];
            var types = new Class<?>[components.length];
            for (var index = 0; index < components.length; index++) {
                types[index] = components[index].getType();
            }
            var constructor = type.getDeclaredConstructor(types);
            var parameters = constructor.getParameters();
            for (var index = 0; index < components.length; index++) {
                var component = components[index];
                var property = parameters[index].getAnnotation(Property.class);
                if (property == null) {
                    property = component.getAnnotation(Property.class);
                }
                var env = parameters[index].getAnnotation(Env.class);
                var name = property == null || property.value().isEmpty() ? component.getName() : property.value();
                arguments[index] = new PropertyRequest(env != null ? env.value() : prefix.isEmpty() ? name : prefix + "." + name,
                        Types.member(context, type, component.getGenericType()), property, env).resolve(source);
            }
            if (!constructor.trySetAccessible()) {
                throw new PropertyBindingException("Cannot access record: " + type.getName());
            }
            return new PreparedProperty(() -> {
                var values = arguments.clone();
                for (var index = 0; index < values.length; index++) {
                    values[index] = PreparedProperty.materialize(values[index]);
                }
                try {
                    return constructor.newInstance(values);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new PropertyBindingException("Cannot construct property record: " + type.getName());
                }
            });
        } catch (NoSuchMethodException e) {
            throw new PropertyBindingException("Missing canonical record constructor: " + type.getName());
        } finally {
            VISITING.get().remove(type);
            if (VISITING.get().isEmpty()) {
                VISITING.remove();
            }
        }
    }

}
