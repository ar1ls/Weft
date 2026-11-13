package com.github.ar1ls.weft.runtime.fixtures.completion;

public record ValueRepository<T>(T value) implements Repository<T> {
}
