package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Property;

import java.util.List;

public class UnsupportedPropertyBean {

    @Property(value = "app.value", defaultValue = "one,two")
    private List<String> value;

}
