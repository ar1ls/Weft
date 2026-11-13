package com.github.ar1ls.weft.runtime.fixtures.propertiesadvanced;

import com.github.ar1ls.weft.api.annotations.Properties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Properties("")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MutableSettings {

    String host;
    int port;

}
