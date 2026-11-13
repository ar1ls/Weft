package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.BeanKey;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SingletonStore {

    Map<BeanKey, CompletableFuture<Object>> instances = new LinkedHashMap<>();
    Map<BeanKey, Thread> owners = new LinkedHashMap<>();
    Map<Thread, BeanKey> waiting = new LinkedHashMap<>();

    synchronized void clear() {
        this.instances.clear();
        this.owners.clear();
        this.waiting.clear();
    }

    Object getOrCreate(BeanKey key, Supplier<?> factory) {
        CompletableFuture<Object> result;
        boolean create;
        var thread = Thread.currentThread();
        synchronized (this) {
            result = this.instances.get(key);
            create = result == null;
            if (create) {
                result = new CompletableFuture<>();
                this.instances.put(key, result);
                this.owners.put(key, thread);
            } else if (!result.isDone()) {
                var owner = this.owners.get(key);
                var path = new ArrayList<BeanKey>();
                path.add(key);
                while (owner != null) {
                    if (owner == thread) {
                        throw new BeanCreationException("Circular singleton creation: " + path);
                    }
                    var target = this.waiting.get(owner);
                    if (target == null) {
                        break;
                    }
                    path.add(target);
                    owner = this.owners.get(target);
                }
                this.waiting.put(thread, key);
            }
        }
        if (create) {
            try {
                var instance = factory.get();
                if (instance == null) {
                    throw new BeanCreationException("Null singleton result: " + key);
                }
                result.complete(instance);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                synchronized (this) {
                    this.owners.remove(key);
                }
            }
        }
        try {
            return result.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (failure.getCause() instanceof Error cause) {
                throw cause;
            }
            throw new BeanCreationException("Cannot create singleton: " + key, failure.getCause());
        } finally {
            synchronized (this) {
                this.waiting.remove(thread);
            }
        }
    }

}
