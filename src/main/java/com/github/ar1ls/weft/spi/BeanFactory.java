package com.github.ar1ls.weft.spi;

import com.github.ar1ls.weft.definition.DependencyDescriptor;

import java.util.List;

public interface BeanFactory {

    List<DependencyDescriptor> dependencies();

    Object create(Object[] arguments) throws Exception;

}
