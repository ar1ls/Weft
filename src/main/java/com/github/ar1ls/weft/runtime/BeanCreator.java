package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;

import java.lang.reflect.InvocationTargetException;

public class BeanCreator {

    public <T> T create(Class<T> type) {
        try {
            var factory = new ConstructorBeanFactory(type);
            return type.cast(factory.create(new Object[0]));
        } catch (NoSuchMethodException e) {
            throw new BeanCreationException("Public no-arg constructor required: " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("Constructor failed: " + type.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException("Cannot instantiate: " + type.getName(), e);
        }
    }

}
