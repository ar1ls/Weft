package com.github.ar1ls.weft.runtime.fixtures.regression;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Primary;

@Configuration
@Primary
public class ChildConfiguration extends BaseConfiguration {

    @Bean
    @Named("child")
    @Override
    public OwnerIdentity identity() {
        return new OwnerIdentity(this);
    }

}
