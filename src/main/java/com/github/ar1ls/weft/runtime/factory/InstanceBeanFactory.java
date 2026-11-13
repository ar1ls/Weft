package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InstanceBeanFactory implements BeanFactory {

    Object instance;

    @Getter(onMethod_ = @Override)
    @Accessors(fluent = true)
    boolean owned;

    @Override
    public List<DependencyDescriptor> dependencies() {
        return List.of();
    }

    @Override
    public Object create(Object[] arguments) {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("External instance has no factory arguments");
        }
        return Objects.requireNonNull(this.instance);
    }

    @Override
    public boolean initialize() {
        return false;
    }

}
