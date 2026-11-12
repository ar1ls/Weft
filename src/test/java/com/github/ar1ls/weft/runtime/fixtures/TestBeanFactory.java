package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.spi.BeanFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public record TestBeanFactory(List<DependencyDescriptor> dependencies, Supplier<?> supplier) implements BeanFactory {

    public TestBeanFactory {
        dependencies = List.copyOf(dependencies);
        Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public Object create(Object[] arguments) {
        return this.supplier.get();
    }

}
