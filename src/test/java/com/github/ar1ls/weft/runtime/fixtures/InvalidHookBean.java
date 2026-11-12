package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PostConstruct;

public class InvalidHookBean {

    @PostConstruct
    private String initialize(SimpleBean simple) {
        return "invalid";
    }

}
