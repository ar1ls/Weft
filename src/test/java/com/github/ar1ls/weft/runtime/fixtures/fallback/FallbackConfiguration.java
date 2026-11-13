package com.github.ar1ls.weft.runtime.fixtures.fallback;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Configuration
@ConditionalOnMissingBean(Runnable.class)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FallbackConfiguration {

    SimpleBean dependency;

    @Bean
    public Runnable task() {
        return () -> this.dependency.toString();
    }

}
