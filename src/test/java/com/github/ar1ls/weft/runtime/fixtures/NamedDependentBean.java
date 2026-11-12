package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.Named;

public record NamedDependentBean(@Named("selected") SimpleBean dependency) {
}
