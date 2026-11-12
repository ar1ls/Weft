package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PreDestroy;

public class MultipleDestroyBean {

    @PreDestroy
    private void first() {
    }

    @PreDestroy
    private void second() {
    }

}
