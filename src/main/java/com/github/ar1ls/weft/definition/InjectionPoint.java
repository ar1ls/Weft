package com.github.ar1ls.weft.definition;

import java.util.Objects;

public record InjectionPoint(String description) {

    public InjectionPoint {
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Injection point must not be blank");
        }
    }

}
