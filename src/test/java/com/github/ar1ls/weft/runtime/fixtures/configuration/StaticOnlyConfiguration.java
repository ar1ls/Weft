package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Lazy;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

@Configuration
@Lazy
public class StaticOnlyConfiguration {

    public StaticOnlyConfiguration() {
        throw new IllegalStateException("Configuration must remain lazy");
    }

    @Bean
    public static SimpleBean simple() {
        return new SimpleBean();
    }

}
