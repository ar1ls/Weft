package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PreDestroy;

public class InvalidDestroyBean {

    @PreDestroy
    private static void destroy() {
    }

}
