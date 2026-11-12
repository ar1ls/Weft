package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.plan.CreationPlan;
import com.github.ar1ls.weft.plan.DependencyResolver;
import com.github.ar1ls.weft.plan.InitializationPlan;
import com.github.ar1ls.weft.plan.PlanBuilder;
import com.github.ar1ls.weft.plan.ResolvedDependency;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class WeftRuntime implements AutoCloseable {

    final DependencyResolver resolver;
    final InitializationPlan plan;
    final BeanCreator creator = new BeanCreator();
    final SingletonStore singletons = new SingletonStore();
    final ShutdownCoordinator shutdown = new ShutdownCoordinator();
    final Set<BeanKey> creatingPrototypes = new HashSet<>();
    final List<BeanKey> creatingBeans = new ArrayList<>();
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
            this.state = RuntimeState.CLOSING;
            try {
                this.shutdown.close();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            } finally {
                this.singletons.clear();
                this.state = RuntimeState.FAILED;
            }
            throw e;
        }
    }

    @Override
    public void close() {
        this.requireOwnerThread();
        if (this.state == RuntimeState.CLOSED || this.state == RuntimeState.CLOSING) {
            return;
        }
        if (this.state == RuntimeState.STARTING || !this.creatingBeans.isEmpty()) {
            throw new IllegalStateException("Cannot close runtime during bean creation");
        }
        this.state = RuntimeState.CLOSING;
        try {
            this.shutdown.close();
        } finally {
            this.singletons.clear();
            this.state = RuntimeState.CLOSED;
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
        if (!this.creatingBeans.isEmpty()) {
            this.shutdown.recordDependency(this.creatingBeans.getLast(), definition.key());
        }
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
        this.creatingBeans.add(plan.definition().key());
        try {
            return this.instantiate(plan);
        } finally {
            this.creatingBeans.removeLast();
        }
    }

    private Object instantiate(CreationPlan plan) {
        var arguments = new Object[plan.arguments().size()];
        for (var index = 0; index < arguments.length; index++) {
            arguments[index] = this.resolveDependency(plan.arguments().get(index));
        }
        var bean = this.creator.create(plan.definition(), arguments);
        try {
            this.initialize(plan, bean);
        } catch (RuntimeException | Error e) {
            var previousState = this.state;
            this.state = RuntimeState.CLOSING;
            try {
                var cleanup = this.shutdown.destroyFailed(new ManagedBean(plan, bean));
                if (cleanup != null) {
                    e.addSuppressed(cleanup);
                }
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            } finally {
                this.state = previousState;
            }
            throw e;
        }
        if (plan.definition().scope() == BeanScope.SINGLETON) {
            this.shutdown.track(new ManagedBean(plan, bean));
        }
        return bean;
    }

    private void initialize(CreationPlan plan, Object bean) {
        for (var injection : plan.fields()) {
            var value = this.resolveDependency(injection.dependency());
            try {
                injection.field().set(bean, value);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new BeanCreationException("Cannot inject field " + injection.field().toGenericString()
                        + " for bean: " + plan.definition().key(), e);
            }
        }
        for (var hook : plan.postConstruct()) {
            try {
                hook.invoke(bean);
            } catch (InvocationTargetException e) {
                throw new BeanCreationException("PostConstruct failed at " + hook.toGenericString()
                        + " for bean: " + plan.definition().key(), e.getCause());
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw new BeanCreationException("Cannot invoke PostConstruct " + hook.toGenericString()
                        + " for bean: " + plan.definition().key(), e);
            }
        }
    }

    private Object resolveDependency(ResolvedDependency dependency) {
        var target = this.plan.beans().get(dependency.target()).definition();
        return switch (dependency.descriptor().kind()) {
            case DIRECT -> this.resolve(target);
            case PROVIDER -> this.providerFor(target);
            default -> throw new IllegalStateException("Unsupported planned dependency: " + dependency.descriptor());
        };
    }

    private Provider<?> providerFor(BeanDefinition definition) {
        var consumer = this.creatingBeans.getLast();
        return () -> {
            this.requireOwnerThread();
            if (this.state != RuntimeState.STARTING && this.state != RuntimeState.RUNNING) {
                throw new IllegalStateException("Cannot use provider in runtime state: " + this.state);
            }
            this.shutdown.recordDependency(consumer, definition.key());
            return this.resolve(definition);
        };
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != this.ownerThread) {
            throw new IllegalStateException("Runtime currently supports only its owner thread: "
                    + this.ownerThread.getName());
        }
    }

}
