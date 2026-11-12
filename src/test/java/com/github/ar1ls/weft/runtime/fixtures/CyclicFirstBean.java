package com.github.ar1ls.weft.runtime.fixtures;

public record CyclicFirstBean(CyclicSecondBean dependency) {
}
