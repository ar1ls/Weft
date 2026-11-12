package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.plan.CreationPlan;
import com.github.ar1ls.weft.plan.DependencyResolver;
import com.github.ar1ls.weft.plan.InitializationPlan;
import com.github.ar1ls.weft.plan.PlanBuilder;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class WeftRuntime {

    final DependencyResolver resolver;
    final InitializationPlan plan;
    final BeanCreator creator = new BeanCreator();
    final SingletonStore singletons = new SingletonStore();
    final Set<BeanKey> creatingPrototypes = new HashSet<>();
    final Thread ownerThread = Thread.currentThread();

    RuntimeState state = RuntimeState.NEW;

    public WeftRuntime(@NonNull BeanRegistry registry) {
        this.resolver = new DependencyResolver(registry.freeze().values());
        this.plan = new PlanBuilder().build(this.resolver);
    }

    public void start() {
        this.requireOwnerThread();
        if (this.state != RuntimeState.NEW) {
            throw new IllegalStateException("Cannot start runtime in state: " + this.state);
        }

        this.state = RuntimeState.STARTING;
        try {
            for (var key : this.plan.creationOrder()) {
                var definition = this.plan.beans().get(key).definition();
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

        var definition = this.resolver.resolve(type);
        return type.cast(this.resolve(definition));
    }

    private Object resolve(BeanDefinition definition) {
        if (definition.scope() == BeanScope.PROTOTYPE) {
            if (!this.creatingPrototypes.add(definition.key())) {
                throw new BeanCreationException("Circular prototype creation: " + definition.key());
            }
            try {
                return this.create(this.plan.beans().get(definition.key()));
            } finally {
                this.creatingPrototypes.remove(definition.key());
            }
        }
        return this.singletons.getOrCreate(definition.key(), () -> this.create(this.plan.beans().get(definition.key())));
    }

    private Object create(CreationPlan plan) {
        var arguments = new Object[plan.arguments().size()];
        for (var index = 0; index < arguments.length; index++) {
            var dependency = this.plan.beans().get(plan.arguments().get(index));
            arguments[index] = this.resolve(dependency.definition());
        }
        return this.creator.create(plan.definition(), arguments);
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != this.ownerThread) {
            throw new IllegalStateException("Runtime currently supports only its owner thread: "
                    + this.ownerThread.getName());
        }
    }

}
