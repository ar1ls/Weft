package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.Inject;

public class MultipleInjectConstructorsBean {

    @Inject
    public MultipleInjectConstructorsBean() {
    }

    @Inject
    public MultipleInjectConstructorsBean(SimpleBean dependency) {
    }

}
