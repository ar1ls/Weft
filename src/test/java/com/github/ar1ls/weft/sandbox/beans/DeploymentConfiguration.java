package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;

@Configuration
public class DeploymentConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeploymentMessage fallback() {
        return () -> "Default mode";
    }

    @Bean
    @ConditionalOnProperty(prefix = "app", name = "mode", havingValue = "dev")
    public DeploymentMessage development() {
        return () -> "Development mode";
    }

    @Bean
    @ConditionalOnProperty(prefix = "app", name = "mode", havingValue = "prod")
    public DeploymentMessage production() {
        return () -> "Production mode";
    }

}
