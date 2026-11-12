package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PostConstruct;

public class MultipleHooksBean {

    @PostConstruct
    private void first() {
    }

    @PostConstruct
    private void second() {
    }

}
