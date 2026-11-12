package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.Property;

public class ConflictingPropertyBean {

    @Env("APP_VALUE")
    @Property("app.value")
    private String value;

}
