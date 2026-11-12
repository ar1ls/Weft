package com.github.ar1ls.weft.runtime.fixtures;

public class SuppressedHookBean extends OverrideHookBase {

    @Override
    protected void initialize() {
        this.getEvents().add("override");
    }

}
