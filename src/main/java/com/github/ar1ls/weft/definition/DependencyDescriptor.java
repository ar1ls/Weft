package com.github.ar1ls.weft.definition;

import com.github.ar1ls.weft.properties.PropertyRequest;

import java.util.Objects;

public record DependencyDescriptor(TypeRef<?> type, String name, DependencyKind kind, InjectionPoint injectionPoint,
                                   PropertyRequest property) {

    public DependencyDescriptor(TypeRef<?> type, String name, DependencyKind kind, InjectionPoint point) {
        this(type, name, kind, point, null);
    }

    public DependencyDescriptor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(injectionPoint, "injectionPoint");
        if ((kind == DependencyKind.VALUE) == (property == null)) {
            throw new IllegalArgumentException("Only VALUE dependencies require a property request");
        }
        name = name == null ? "" : name.strip();
    }

}
