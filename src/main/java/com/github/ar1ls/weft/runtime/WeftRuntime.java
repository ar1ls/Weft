package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class WeftRuntime {

    final Map<Class<?>, BeanDefinition> definitions;
    final BeanCreator creator = new BeanCreator();
    final SingletonStore singletons = new SingletonStore();
    final Set<BeanKey> creatingPrototypes = new HashSet<>();
    final Thread ownerThread = Thread.currentThread();

    RuntimeState state = RuntimeState.NEW;

    public WeftRuntime(@NonNull BeanRegistry registry) {
        var definitionsByType = new LinkedHashMap<Class<?>, BeanDefinition>();
        for (var definition : registry.freeze().values()) {
            if (!(definition.type().type() instanceof Class<?> type)) {
                throw new DefinitionException("Only class types are supported: " + definition.key());
            }
            if (!definition.factory().dependencies().isEmpty()) {
                throw new DefinitionException("Factory dependencies are not supported yet: " + definition.key());
            }
            var previous = definitionsByType.putIfAbsent(type, definition);
            if (previous != null) {
                throw new DefinitionException("Multiple beans for type " + type.getName()
                        + ": " + previous.key() + " and " + definition.key());
            }
        }
        this.definitions = Collections.unmodifiableMap(definitionsByType);
    }

    public void start() {
        this.requireOwnerThread();
        if (this.state != RuntimeState.NEW) {
            throw new IllegalStateException("Cannot start runtime in state: " + this.state);
        }

        this.state = RuntimeState.STARTING;
        try {
            for (var definition : this.definitions.values()) {
                if (definition.scope() == BeanScope.SINGLETON && !definition.lazy()) {
                    this.resolve(definition);
                }
            }
            this.state = RuntimeState.RUNNING;
        } catch (RuntimeException | Error e) {
            this.state = RuntimeState.FAILED;
            throw e;
        }
    }

    public <T> T get(@NonNull Class<T> type) {
        this.requireOwnerThread();
        if (this.state != RuntimeState.RUNNING) {
            throw new IllegalStateException("Cannot get bean in runtime state: " + this.state);
        }

        var definition = this.definitions.get(type);
        if (definition == null) {
            throw new MissingBeanException("No bean registered for type: " + type.getName());
        }
        return type.cast(this.resolve(definition));
    }

    private Object resolve(BeanDefinition definition) {
        if (definition.scope() == BeanScope.PROTOTYPE) {
            if (!this.creatingPrototypes.add(definition.key())) {
                throw new BeanCreationException("Circular prototype creation: " + definition.key());
            }
            try {
                return this.creator.create(definition);
            } finally {
                this.creatingPrototypes.remove(definition.key());
            }
        }
        return this.singletons.getOrCreate(definition.key(), () -> this.creator.create(definition));
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != this.ownerThread) {
            throw new IllegalStateException("Runtime currently supports only its owner thread: "
                    + this.ownerThread.getName());
        }
    }

}
