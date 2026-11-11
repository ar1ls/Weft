package com.github.ar1ls.weft.api;

@FunctionalInterface
public interface Provider<T> {

    T get();

}
