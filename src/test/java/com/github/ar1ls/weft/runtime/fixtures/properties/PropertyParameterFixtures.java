package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.Property;

public class PropertyParameterFixtures {

    public void property(@Property("app.value") String value) {
    }

    public void environment(@Env("APP_VALUE") int value) {
    }

}
