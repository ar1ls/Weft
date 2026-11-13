package com.github.ar1ls.weft.properties;

import lombok.NonNull;

import java.util.function.Supplier;

public record PreparedProperty(Supplier<?> factory) {

    public Object materialize() {
        return this.factory.get();
    }

    public static Object materialize(Object value) {
        return value instanceof PreparedProperty prepared ? prepared.materialize() : value;
    }

    @Override
    public @NonNull String toString() {
        return "PreparedProperty[redacted]";
    }

}
