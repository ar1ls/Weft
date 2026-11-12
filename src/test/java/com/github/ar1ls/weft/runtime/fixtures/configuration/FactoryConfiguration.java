package com.github.ar1ls.weft.runtime.fixtures.configuration;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.PairDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Configuration
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FactoryConfiguration {

    final SimpleBean dependency;
    ConstructorDependentBean lastDependent;

    @Bean
    public ConstructorDependentBean dependent(SimpleBean dependency) {
        this.lastDependent = new ConstructorDependentBean(dependency);
        return this.lastDependent;
    }

    @Bean
    public PairDependentBean pair(SimpleBean first, ConstructorDependentBean second) {
        return new PairDependentBean(first, second);
    }

    @Bean
    public static Object marker() {
        return new Object();
    }

}
