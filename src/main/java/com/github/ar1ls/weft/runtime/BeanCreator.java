package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.BeanCreationException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class BeanCreator {

    public <T> T create(Class<T> type) {
        Constructor<T> constructor;
        try {
            constructor = type.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new BeanCreationException("Public no-arg constructor required: " + type.getName(), e);
        }
        try {
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new BeanCreationException("Cannot instantiate: " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("Constructor failed: " + type.getName(), e.getCause());
        }
    }

}
