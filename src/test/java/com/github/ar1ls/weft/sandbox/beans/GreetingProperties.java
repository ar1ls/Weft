package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Bean
@Properties("greeting")
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GreetingProperties {

    @Property(defaultValue = "Hello, ")
    String text;

    @Property(defaultValue = "3")
    int retries;

    @Property(defaultValue = "PT5S")
    Duration timeout;

}
