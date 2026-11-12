package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.BeanKey;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SingletonStore {

    Map<BeanKey, Object> instances = new LinkedHashMap<>();
    Map<BeanKey, Throwable> failures = new LinkedHashMap<>();
    Set<BeanKey> creating = new HashSet<>();

    Object getOrCreate(BeanKey key, Supplier<?> factory) {
        var existing = this.instances.get(key);
        if (existing != null) {
            return existing;
        }
        var failure = this.failures.get(key);
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (!this.creating.add(key)) {
            throw new BeanCreationException("Circular singleton creation: " + key);
        }

        try {
            var instance = factory.get();
            if (instance == null) {
                throw new BeanCreationException("Null singleton result: " + key);
            }
            this.instances.put(key, instance);
            return instance;
        } catch (RuntimeException | Error e) {
            this.failures.put(key, e);
            throw e;
        } finally {
            this.creating.remove(key);
        }
    }

}
