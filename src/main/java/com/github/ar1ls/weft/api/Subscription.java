package com.github.ar1ls.weft.api;

@FunctionalInterface
public interface Subscription extends AutoCloseable {

    @Override
    void close();

}
