package com.github.ar1ls.weft.runtime.fixtures.regression;

public class RecursiveUnmanaged {

    public RecursiveUnmanaged(Runnable callback) {
        callback.run();
    }

}
