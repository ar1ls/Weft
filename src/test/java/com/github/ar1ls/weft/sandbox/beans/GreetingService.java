package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.annotations.Bean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Bean
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GreetingService {

    @Getter
    Formatter formatter;

    GreetingProperties properties;

    public String greet(String name) {
        return this.formatter.format(this.properties.getText() + name);
    }

}
