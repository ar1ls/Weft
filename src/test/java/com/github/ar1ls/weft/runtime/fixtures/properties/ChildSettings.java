package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Properties("child")
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChildSettings extends BaseSettings {

    private static final String CONSTANT = "ignored";

    int count;

    @Inject
    SimpleBean dependency;

}
