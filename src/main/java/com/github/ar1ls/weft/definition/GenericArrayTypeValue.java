package com.github.ar1ls.weft.definition;

import lombok.NonNull;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

public record GenericArrayTypeValue(Type component) implements GenericArrayType {

    @Override
    public @NonNull Type getGenericComponentType() {
        return this.component;
    }

    @Override
    public String getTypeName() {
        return this.component.getTypeName() + "[]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GenericArrayType type && this.component.equals(type.getGenericComponentType());
    }

    @Override
    public int hashCode() {
        return this.component.hashCode();
    }

}
