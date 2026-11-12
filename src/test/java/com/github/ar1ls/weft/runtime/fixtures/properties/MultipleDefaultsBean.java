package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Property;

public class MultipleDefaultsBean {

    @Property(value = "app.value", defaultValue = {"one", "two"})
    private String value;

}
