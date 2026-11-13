package com.github.ar1ls.weft.discovery.fixtures.condition;

import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;

@Configuration
@ConditionalOnMissingBean
public class UnsupportedFallbackConfiguration {
}
