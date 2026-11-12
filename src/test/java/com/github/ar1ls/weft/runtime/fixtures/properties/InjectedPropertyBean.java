package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Property;

public class InjectedPropertyBean {

    @Inject
    @Property("app.value")
    private String value;

}
