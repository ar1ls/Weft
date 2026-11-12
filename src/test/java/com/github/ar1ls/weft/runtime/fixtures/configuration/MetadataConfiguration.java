package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Lazy;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Primary;
import com.github.ar1ls.weft.api.annotations.Scope;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

@Configuration
@Lazy
@Named("configuration")
@Scope(BeanScope.PROTOTYPE)
public class MetadataConfiguration {

    @Bean
    @Lazy
    @Primary
    @Named("product")
    @Scope(BeanScope.PROTOTYPE)
    public SimpleBean simple() {
        return new SimpleBean();
    }

}
