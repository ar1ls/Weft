package com.github.ar1ls.weft.discovery.fixtures.condition;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(prefix = "app", name = "enabled")
public class ConditionalConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app", name = "mode", havingValue = "dev")
    public String development() {
        return "development";
    }

    @Bean
    @ConditionalOnProperty(prefix = "app", name = "mode", havingValue = "prod")
    public String production() {
        return "production";
    }

    @Bean
    @ConditionalOnProperty(name = "unsupported.enabled", havingValue = "true")
    public String unsupported(Provider rawProvider) {
        throw new AssertionError("Disabled factory must not run");
    }

}
