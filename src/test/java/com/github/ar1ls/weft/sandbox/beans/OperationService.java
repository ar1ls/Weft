package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Bean;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Bean
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OperationService {

    Provider<OperationContext> contexts;

    public OperationContext next() {
        return this.contexts.get();
    }

}
