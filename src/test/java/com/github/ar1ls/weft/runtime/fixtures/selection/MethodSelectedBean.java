package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.Provider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record MethodSelectedBean(Formatter named, Optional<MissingPort> missing, List<Formatter> list, Set<Formatter> set, Provider<Formatter> primary) {
}
