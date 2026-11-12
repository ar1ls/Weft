package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;

@Configuration
public class VoidFactoryConfiguration {

    @Bean
    public void invalid() {
    }

}
