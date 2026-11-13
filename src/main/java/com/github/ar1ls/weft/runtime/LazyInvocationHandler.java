package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.Provider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class LazyInvocationHandler implements InvocationHandler {

    Class<?> contract;
    Provider<?> target;

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "WeftLazy[" + this.contract.getName() + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
        var instance = this.target.get();
        if (!method.canAccess(instance)) {
            method.trySetAccessible();
        }
        try {
            return method.invoke(instance, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

}
