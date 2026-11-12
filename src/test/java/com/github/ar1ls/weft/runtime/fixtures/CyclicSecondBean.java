package com.github.ar1ls.weft.runtime.fixtures;

public record CyclicSecondBean(CyclicFirstBean dependency) {
}
