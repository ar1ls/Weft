package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.runtime.fixtures.ProviderDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

@Configuration
public class ProviderMethodConfiguration {

    @Bean
    public ProviderDependentBean consumer(Provider<SimpleBean> provider) {
        return new ProviderDependentBean(provider);
    }

}
