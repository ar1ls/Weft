package com.github.ar1ls.weft.discovery.fixtures.condition;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;

@Bean
@ConditionalOnEnv(name = "DISABLED_BEAN", havingValue = "enabled")
public class DisabledBean {

    public DisabledBean(Provider rawProvider) {
        throw new AssertionError("Disabled constructor must not run");
    }

}
