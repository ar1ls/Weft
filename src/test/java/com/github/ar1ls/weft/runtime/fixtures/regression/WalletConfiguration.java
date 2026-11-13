package com.github.ar1ls.weft.runtime.fixtures.regression;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;

@Configuration
public class WalletConfiguration {

    @Bean
    public Wallet wallet(LifecycleTrace trace) {
        return new WalletImpl(trace);
    }

}
