package com.github.ar1ls.weft.discovery.fixtures.condition;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;

@Bean
@ConditionalOnMissingBean(Runnable.class)
public class FallbackTask implements Runnable {

    public FallbackTask(SimpleBean dependency) {
        throw new AssertionError("Suppressed fallback must not be constructed");
    }

    @Override
    public void run() {}

}
