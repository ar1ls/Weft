package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Env;

public class StaticPropertyBean {

    @Env("APP_STATIC")
    private static String value;

}
