package com.github.ar1ls.weft.definition;

import lombok.NonNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public record ParameterizedTypeValue(Type rawType, Type ownerType, Type[] arguments) implements ParameterizedType {

    public ParameterizedTypeValue {
        Objects.requireNonNull(rawType);
        arguments = arguments.clone();
    }

    @Override
    public Type[] arguments() {
        return this.arguments.clone();
    }

    @Override
    public Type[] getActualTypeArguments() {
        return this.arguments.clone();
    }

    @Override
    public @NonNull Type getRawType() {
        return this.rawType;
    }

    @Override
    public Type getOwnerType() {
        return this.ownerType;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ParameterizedType type && Objects.equals(this.ownerType, type.getOwnerType())
                && this.rawType.equals(type.getRawType()) && Arrays.equals(this.arguments, type.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.arguments) ^ Objects.hashCode(this.ownerType) ^ this.rawType.hashCode();
    }

    @Override
    public String getTypeName() {
        var result = new StringJoiner(", ", this.rawType.getTypeName() + "<", ">");
        for (var argument : this.arguments) {
            result.add(argument.getTypeName());
        }
        return result.toString();
    }

    @Override
    public @NonNull String toString() {
        return this.getTypeName();
    }

}
