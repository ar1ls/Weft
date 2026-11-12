package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.nio.file.Path;
import java.time.Duration;

@Properties("greeting")
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GreetingSettings {

    @Property(defaultValue = "Hello, ")
    String text;

    @Property(value = "attempts", defaultValue = "3")
    int retries;

    @Property(defaultValue = "true")
    boolean enabled;

    @Property(defaultValue = "PT5S")
    Duration timeout;

    @Property(defaultValue = "DEFAULT")
    BindingMode mode;

    @Property(defaultValue = ".")
    Path directory;

    @Property(defaultValue = {""})
    String empty;

}
