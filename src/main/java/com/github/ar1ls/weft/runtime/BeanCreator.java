package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;

import java.lang.reflect.InvocationTargetException;

public class BeanCreator {

    public Object create(BeanDefinition definition) {
        if (!(definition.type().type() instanceof Class<?> type)) {
            throw new BeanCreationException("Only class types are supported: " + definition.key());
        }
        if (!definition.factory().dependencies().isEmpty()) {
            throw new BeanCreationException("Factory dependencies are not supported yet: " + definition.key());
        }

        Object bean;
        try {
            bean = definition.factory().create(new Object[0]);
        } catch (InvocationTargetException e) {
            throw new BeanCreationException("Factory failed for bean: " + definition.key(), e.getCause());
        } catch (Exception e) {
            throw new BeanCreationException("Cannot create bean: " + definition.key(), e);
        }

        if (!type.isInstance(bean)) {
            throw new BeanCreationException("Invalid factory result for bean: " + definition.key());
        }
        return bean;
    }

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
