package com.github.ar1ls.weft.api;

import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.InstanceBeanFactory;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.function.Supplier;

@UtilityClass
public class Definitions {

    public <T> BeanDefinition of(Class<T> type) {
        return of(TypeRef.of(type));
    }

    public BeanDefinition of(TypeRef<?> type) {
        Types.validate(type.type());
        return definition(type.toString(), type, BeanScope.SINGLETON, false, new ConstructorBeanFactory(type.type()));
    }

    public <T> BeanDefinition of(Class<T> type, String sourceId) {
        return of(TypeRef.of(type), sourceId);
    }

    public BeanDefinition of(TypeRef<?> type, String sourceId) {
        return definition(type.toString(), type, BeanScope.SINGLETON, false, new ConstructorBeanFactory(type.type()), sourceId);
    }

    public BeanDefinition supplied(String id, TypeRef<?> type, BeanScope scope, boolean lazy, Supplier<?> supplier) {
        return definition(id, type, scope, lazy, new SupplierBeanFactory(supplier));
    }

    public <T> BeanDefinition instance(String id, Class<T> type, T instance, boolean owned) {
        if (!type.isInstance(Objects.requireNonNull(instance))) {
            throw new IllegalArgumentException("Invalid external instance type");
        }
        return definition(id, TypeRef.of(type), BeanScope.SINGLETON, false, new InstanceBeanFactory(instance, owned));
    }

    private BeanDefinition definition(String id, TypeRef<?> type, BeanScope scope, boolean lazy, BeanFactory factory) {
        return definition(id, type, scope, lazy, factory, "programmatic");
    }

    private BeanDefinition definition(String id, TypeRef<?> type, BeanScope scope, boolean lazy, BeanFactory factory, String sourceId) {
        Types.validate(type.type());
        var loader = Types.raw(type.type()).getClassLoader();
        var source = new BeanSource(sourceId, loader == null ? Definitions.class.getClassLoader() : loader);
        return new BeanDefinition(new BeanKey(source.id(), id), type, "", scope, lazy, false, source, factory);
    }

}
