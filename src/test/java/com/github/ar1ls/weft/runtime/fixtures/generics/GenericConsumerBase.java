package com.github.ar1ls.weft.runtime.fixtures.generics;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.runtime.fixtures.completion.Repository;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class GenericConsumerBase<T> {

    @Inject
    @Getter
    Repository<T> repository;

}
