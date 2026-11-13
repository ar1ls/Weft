package com.github.ar1ls.weft.definition;

import lombok.NonNull;

import java.lang.reflect.Type;
import java.util.Objects;

public record TypeRef<T>(Type type) {

    public TypeRef {
        Objects.requireNonNull(type, "type");
    }

    public static <T> TypeRef<T> of(Class<T> type) {
        return new TypeRef<>(type);
    }

    public static TypeRef<?> of(Type type) {
        return new TypeRef<>(type);
    }

    public static <T> TypeRef<T> parameterized(Class<?> raw, TypeRef<?>... arguments) {
        var types = new Type[arguments.length];
        for (var index = 0; index < types.length; index++) {
            types[index] = arguments[index].type();
        }
        var result = new ParameterizedTypeValue(raw, raw.getDeclaringClass(), types);
        Types.validate(result);
        return new TypeRef<>(result);
    }

    @Override
    public @NonNull String toString() {
        return this.type.getTypeName();
    }

}
