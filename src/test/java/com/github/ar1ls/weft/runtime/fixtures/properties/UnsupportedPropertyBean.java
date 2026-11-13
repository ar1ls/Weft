package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Property;

import java.net.URI;

public class UnsupportedPropertyBean {

    @Property(value = "app.value", defaultValue = "one,two")
    private URI value;

}
