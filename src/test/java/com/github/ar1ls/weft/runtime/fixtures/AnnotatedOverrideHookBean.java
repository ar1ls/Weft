package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PostConstruct;

public class AnnotatedOverrideHookBean extends OverrideHookBase {

    @Override
    @PostConstruct
    protected void initialize() {
        this.getEvents().add("override");
    }

}
