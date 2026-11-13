package com.github.ar1ls.weft.api;

import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.definition.Types;

import java.util.Objects;

public record SourceDefault(String sourceId, TypeRef<?> type, BeanKey target) {

    public SourceDefault {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("Consumer source id must not be blank");
        }
        Types.validate(type.type());
    }

}
