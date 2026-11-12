package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Named;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ConstructorSelectionBean(
        Formatter primary,
        @Named("plain") Formatter named,
        @Named("plain") Provider<Formatter> provider,
        Optional<Formatter> optional,
        List<Formatter> list,
        @Named("plain") Set<Formatter> namedSet
) {
}
