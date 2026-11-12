package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

@Configuration
public record CyclicConfiguration(ConstructorDependentBean dependency) {

    @Bean
    public ConstructorDependentBean dependent(SimpleBean simple) {
        return new ConstructorDependentBean(simple);
    }

}
