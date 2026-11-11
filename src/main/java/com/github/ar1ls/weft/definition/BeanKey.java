package com.github.ar1ls.weft.definition;

import java.util.Objects;

public record BeanKey(String sourceId, String localId) {

    public BeanKey {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(localId, "localId");
        if (sourceId.isBlank() || localId.isBlank()) {
            throw new IllegalArgumentException("Bean key parts must not be blank");
        }
    }
}
