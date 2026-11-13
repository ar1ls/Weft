package com.github.ar1ls.weft.runtime.fixtures.contextual;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;

@Configuration
public class SourceConfiguration {

    @Bean
    @Named("factory")
    public SourceConsumerView consumer(SourceLabel constructorLabel) {
        return new SourceConsumer(constructorLabel);
    }

}
