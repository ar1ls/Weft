package com.github.ar1ls.weft.api.annotations;

import com.github.ar1ls.weft.api.BeanScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Scope {
    BeanScope value() default BeanScope.SINGLETON;
}
