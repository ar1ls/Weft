package com.github.ar1ls.weft.runtime.fixtures.generics;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.runtime.fixtures.completion.Repository;
import com.github.ar1ls.weft.runtime.fixtures.completion.ValueRepository;

public abstract class GenericConfigurationBase<T> {

    protected abstract T value();

    @Bean
    public Repository<T> repository() {
        return new ValueRepository<>(this.value());
    }

}
