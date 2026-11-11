package com.github.ar1ls.weft.definition;

import java.util.Objects;

public record DependencyDescriptor(TypeRef<?> type, String name, DependencyKind kind, InjectionPoint injectionPoint) {
    public DependencyDescriptor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(injectionPoint, "injectionPoint");
        name = name == null ? "" : name.strip();
    }
}
