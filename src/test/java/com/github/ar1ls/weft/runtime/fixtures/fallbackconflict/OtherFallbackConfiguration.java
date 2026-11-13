package com.github.ar1ls.weft.runtime.fixtures.fallbackconflict;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;

@Configuration
@ConditionalOnMissingBean(Runnable.class)
public class OtherFallbackConfiguration {

    @Bean
    public static Runnable task() {
        return () -> {};
    }

}
