package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Property;

public class FinalPropertyBean {

    @Property("app.final")
    private final String value = "initial";

}
