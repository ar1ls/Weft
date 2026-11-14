package com.github.ar1ls.weft.sandbox.beans.excluded;

import com.github.ar1ls.weft.api.annotations.Bean;

@Bean
public class GreetingService2 {

    public String greet(String name) {
        return "Hello2, " + name;
    }

}
