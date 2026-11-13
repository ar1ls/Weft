package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SupplierBeanFactory implements BeanFactory {

    @Getter(onMethod_ = @Override)
    @Accessors(fluent = true)
    List<DependencyDescriptor> dependencies;

    Function<Object[], ?> factory;

    public SupplierBeanFactory(@NonNull Supplier<?> supplier) {
        this.dependencies = List.of();
        this.factory = arguments -> supplier.get();
    }

    public SupplierBeanFactory(List<DependencyDescriptor> dependencies, @NonNull Function<Object[], ?> factory) {
        this.dependencies = List.copyOf(dependencies);
        this.factory = factory;
    }

    @Override
    public Object create(@NonNull Object[] arguments) {
        if (arguments.length != this.dependencies.size()) {
            throw new IllegalArgumentException("Expected " + this.dependencies.size() + " supplier arguments");
        }
        return this.factory.apply(arguments.clone());
    }

}
