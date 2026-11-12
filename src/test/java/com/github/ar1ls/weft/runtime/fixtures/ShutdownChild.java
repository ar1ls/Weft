package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PreDestroy;

import java.util.List;

public class ShutdownChild extends ShutdownResource {

    public ShutdownChild(List<String> events) {
        super("base", events);
    }

    @PreDestroy
    private void destroy() {
        this.getEvents().add("child:destroy");
    }

}
