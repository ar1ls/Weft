package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Scope;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Bean
@Scope(BeanScope.PROTOTYPE)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OperationContext {

    UUID id = UUID.randomUUID();

}
