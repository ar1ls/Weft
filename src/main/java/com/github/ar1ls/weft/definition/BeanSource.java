package com.github.ar1ls.weft.definition;

import java.util.Objects;

public record BeanSource(String id, ClassLoader classLoader) {

    public BeanSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(classLoader, "classLoader");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Bean source id must not be blank");
        }
    }

}
