package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

@Configuration
public class NonPublicFactoryConfiguration {

    @Bean
    private SimpleBean simple() {
        return new SimpleBean();
    }

}
