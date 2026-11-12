package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BeanDefinitions {

    public BeanDefinition of(Class<?> type) {
        return of(type, new ConstructorBeanFactory(type));
    }

    public BeanDefinition of(Class<?> type, BeanFactory factory) {
        return of(type, BeanScope.SINGLETON, false, factory);
    }

    public BeanDefinition of(Class<?> type, BeanScope scope, boolean lazy, BeanFactory factory) {
        var source = new BeanSource("test", SimpleBean.class.getClassLoader());
        var key = new BeanKey(source.id(), type.getName());
        return new BeanDefinition(key, TypeRef.of(type), "", scope, lazy, false, source, factory);
    }

}
