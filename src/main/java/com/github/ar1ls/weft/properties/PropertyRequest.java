package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.NonNull;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Optional;

public record PropertyRequest(String key, Type type, Property property, Env env) {

    public PropertyRequest {
        key = key.strip();
        if (key.isBlank() || (property != null && env != null) || (property != null && property.defaultValue().length > 1)) {
            throw new DefinitionException("Invalid property declaration for key: " + key);
        }
    }

    public Object resolve(PropertySource source) {
        var value = source.find(this.key);
        if (value.isEmpty() && this.property != null && this.property.defaultValue().length == 1) {
            value = Optional.of(this.property.defaultValue()[0]);
        }
        if (value.isEmpty() && this.env != null && !this.env.defaultValue().isEmpty()) {
            value = Optional.of(this.env.defaultValue());
        }
        if (value.isEmpty() && this.env != null && !this.env.required()) {
            var raw = Types.raw(this.type);
            return raw.isPrimitive() ? Array.get(Array.newInstance(raw, 1), 0) : null;
        }
        if (value.isEmpty()) {
            if (Types.raw(this.type).isRecord()) {
                return RecordProperties.plan(this.type, this.key, source);
            }
            if (Types.raw(this.type).isAnnotationPresent(Properties.class)) {
                return NestedProperties.plan(this.type, this.key, source);
            }
            throw new PropertyBindingException("Missing property '" + this.key + "'");
        }
        return DefaultValueConverter.convert(this.key, value.get(), this.type);
    }

    @Override
    public @NonNull String toString() {
        return "PropertyRequest[key=" + this.key + ", type=" + this.type.getTypeName() + "]";
    }

}
