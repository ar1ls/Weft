package com.github.ar1ls.weft.spi;

import com.github.ar1ls.weft.definition.BeanDefinition;

@FunctionalInterface
public interface BeanProcessor {

    Object process(BeanDefinition definition, Object instance) throws Exception;

}
