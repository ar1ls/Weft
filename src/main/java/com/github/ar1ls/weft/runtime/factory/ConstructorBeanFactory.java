package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConstructorBeanFactory implements BeanFactory {

    @NonNull
    Class<?> type;

    @Override
    public List<DependencyDescriptor> dependencies() {
        return List.of();
    }

    @Override
    public Object create(Object[] arguments) throws ReflectiveOperationException {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("Constructor arguments are not supported yet");
        }

        var constructor = this.type.getConstructor();
        return constructor.newInstance();
    }

}
