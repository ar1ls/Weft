package com.github.ar1ls.weft.spi;

import com.github.ar1ls.weft.definition.BeanDefinition;

import java.util.Collection;

@FunctionalInterface
public interface DefinitionContributor {

    Collection<BeanDefinition> definitions();

}
