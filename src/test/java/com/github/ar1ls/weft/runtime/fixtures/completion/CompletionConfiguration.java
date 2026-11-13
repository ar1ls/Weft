package com.github.ar1ls.weft.runtime.fixtures.completion;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Property;

@Configuration
public class CompletionConfiguration {

    @Bean
    public Repository<String> text(@Property(value = "message", defaultValue = "ready") String message) {
        return new ValueRepository<>(message);
    }

    @Bean
    public Repository<Integer> number() {
        return new ValueRepository<>(42);
    }

}
