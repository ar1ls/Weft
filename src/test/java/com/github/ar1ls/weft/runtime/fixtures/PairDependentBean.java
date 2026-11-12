package com.github.ar1ls.weft.runtime.fixtures;

public record PairDependentBean(SimpleBean first, ConstructorDependentBean second) {
}
