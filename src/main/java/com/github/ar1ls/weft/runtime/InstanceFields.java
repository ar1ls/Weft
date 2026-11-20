package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.annotations.Instance;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.ShutdownException;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InstanceFields {

    @NonNull
    Object bean;

    List<Field> bound = new ArrayList<>();

    public void bind() {
        var implementation = this.bean.getClass();
        for (var field : implementation.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Instance.class)) {
                continue;
            }
            var modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                throw new DefinitionException("@Instance field must be static and nonfinal: " + field.toGenericString());
            }
            var type = field.getType();
            if (type != implementation && !(type.isInterface() && type.isAssignableFrom(implementation))) {
                throw new DefinitionException("@Instance field must have the implementation type or one of its interfaces: "
                        + field.toGenericString());
            }
            if (!field.trySetAccessible()) {
                throw new DefinitionException("Cannot access @Instance field: " + field.toGenericString());
            }
            synchronized (implementation) {
                try {
                    if (field.get(null) == null) {
                        field.set(null, this.bean);
                        this.bound.add(field);
                    }
                } catch (IllegalAccessException e) {
                    throw new BeanCreationException("Cannot bind @Instance field: " + field.toGenericString(), e);
                }
            }
        }
    }

    public boolean isBound() {
        return !this.bound.isEmpty();
    }

    public void clear(Throwable failure) {
        if (this.bound.isEmpty()) {
            return;
        }
        synchronized (this.bean.getClass()) {
            for (var field : this.bound) {
                try {
                    if (field.get(null) == this.bean) {
                        field.set(null, null);
                    }
                } catch (IllegalAccessException e) {
                    failure.addSuppressed(new ShutdownException("Cannot clear @Instance field: " + field.toGenericString(), e));
                }
            }
            this.bound.clear();
        }
    }

}
