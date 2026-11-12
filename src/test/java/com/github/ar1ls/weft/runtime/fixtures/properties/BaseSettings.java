package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Properties("parent")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BaseSettings {

    @Getter
    @Property(defaultValue = "base")
    String inherited;

}
