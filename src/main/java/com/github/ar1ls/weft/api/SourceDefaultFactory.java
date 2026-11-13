package com.github.ar1ls.weft.api;

import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.Types;

import java.util.Objects;
import java.util.function.Function;

public record SourceDefaultFactory<T>(Class<T> type, Function<BeanSource, ? extends T> factory) {

    public SourceDefaultFactory {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        Types.validate(type);
    }

}
