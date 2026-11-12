package com.github.ar1ls.weft.properties;

import lombok.NonNull;

import java.lang.reflect.Field;

public record PropertyBinding(@NonNull Field field, @NonNull String key, @NonNull Object value) {

    @Override
    public String toString() {
        return "PropertyBinding[field=" + this.field.toGenericString() + ", key=" + this.key + "]";
    }

}
