package com.github.ar1ls.weft.definition;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.spi.BeanFactory;

import java.util.List;
import java.util.Objects;

public record BeanDefinition(
        BeanKey key,
        TypeRef<?> type,
        String name,
        BeanScope scope,
        boolean lazy,
        boolean primary,
        BeanSource source,
        BeanFactory factory,
        List<Class<?>> missingTypes
) {
    public BeanDefinition(BeanKey key, TypeRef<?> type, String name, BeanScope scope, boolean lazy, boolean primary,
            BeanSource source, BeanFactory factory) {
        this(key, type, name, scope, lazy, primary, source, factory, List.of());
    }

    public BeanDefinition {
        Objects.requireNonNull(key);
        Objects.requireNonNull(type);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(source);
        Objects.requireNonNull(factory);
        missingTypes = List.copyOf(missingTypes);
        name = name == null ? "" : name.strip();
        if (!key.sourceId().equals(source.id())) {
            throw new IllegalArgumentException("Bean key source must match definition source");
        }
    }
}
