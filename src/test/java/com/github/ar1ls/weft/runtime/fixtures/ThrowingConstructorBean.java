package com.github.ar1ls.weft.runtime.fixtures;

public class ThrowingConstructorBean {

    public ThrowingConstructorBean() {
        throw new IllegalStateException("Constructor probe");
    }

}