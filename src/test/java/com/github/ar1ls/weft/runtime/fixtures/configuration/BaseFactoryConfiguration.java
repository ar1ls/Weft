package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

public class BaseFactoryConfiguration {

    @Bean
    public SimpleBean simple() {
        return new SimpleBean();
    }

}
