package com.github.ar1ls.weft.runtime.fixtures.regression;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;

@Configuration
public class BaseConfiguration {

    @Bean
    @Named("base")
    public OwnerIdentity identity() {
        return new OwnerIdentity(this);
    }

}
