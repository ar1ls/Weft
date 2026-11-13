package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.Subscription;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.lifecycle.BeanReadyEvent;
import com.github.ar1ls.weft.api.lifecycle.StartupHook;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.plan.*;
import com.github.ar1ls.weft.properties.PreparedProperty;
import com.github.ar1ls.weft.properties.PropertiesBinder;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.spi.BeanProcessor;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WeftRuntime implements AutoCloseable {

    DependencyResolver resolver;
    InitializationPlan plan;
    List<BeanProcessor> processors;
    BeanCreator creator = new BeanCreator();
    SingletonStore singletons = new SingletonStore();
    ShutdownCoordinator shutdown = new ShutdownCoordinator();
    ThreadLocal<Set<BeanKey>> creatingPrototypes = ThreadLocal.withInitial(HashSet::new);
    ThreadLocal<List<BeanKey>> creatingBeans = ThreadLocal.withInitial(ArrayList::new);
    ThreadLocal<Boolean> cleaning = ThreadLocal.withInitial(() -> false);
    RuntimeGate gate = new RuntimeGate();
    PropertyResolver properties;
    String transientSourceId;
    LifecycleSubscriptions subscriptions = new LifecycleSubscriptions();
    Set<BeanKey> published = ConcurrentHashMap.newKeySet();

    public WeftRuntime(@NonNull BeanRegistry registry) {
        this(registry, Map.of());
    }

    public WeftRuntime(BeanRegistry registry, Map<String, String> properties) {
        this(registry, new PropertyResolver(properties));
    }

    public WeftRuntime(@NonNull BeanRegistry registry, @NonNull PropertyResolver properties) {
        this(registry, properties, List.of());
    }

    public WeftRuntime(BeanRegistry registry, Map<String, String> properties, List<? extends BeanProcessor> processors) {
        this(registry, new PropertyResolver(properties), processors);
    }

    public WeftRuntime(@NonNull BeanRegistry registry, @NonNull PropertyResolver properties, List<? extends BeanProcessor> processors) {
        this.processors = List.copyOf(processors);
        this.properties = properties;
        this.resolver = new DependencyResolver(registry.freeze().values());
        this.plan = new PlanBuilder().build(this.resolver, properties);
        var sources = new HashSet<String>();
        for (var definition : this.resolver.definitions()) {
            sources.add(definition.source().id());
        }
        var sourceId = "weft-transient";
        while (sources.contains(sourceId)) {
            sourceId += "_";
        }
        this.transientSourceId = sourceId;
    }

    public void start() {
        this.gate.start();
        var success = false;
        try {
            for (var key : this.plan.creationOrder()) {
                var definition = this.plan.beans().get(key).definition();
                if (definition.scope() == BeanScope.SINGLETON && !definition.lazy()) {
                    this.resolve(definition);
                }
            }
            this.runStartupHooks();
            success = true;
        } catch (RuntimeException | Error e) {
            this.cleaning.set(true);
            try {
                this.shutdown.close();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            } finally {
                this.singletons.clear();
                this.subscriptions.clear();
                this.published.clear();
                this.cleaning.remove();
            }
            throw e;
        } finally {
            this.gate.started(success);
        }
    }

    @Override
    public void close() {
        if (!this.gate.beginClose()) {
            return;
        }
        try {
            this.shutdown.close();
        } finally {
            this.singletons.clear();
            this.subscriptions.clear();
            this.published.clear();
            this.gate.closed();
        }
    }

    private void runStartupHooks() {
        var invoked = Collections.newSetFromMap(new IdentityHashMap<StartupHook, Boolean>());
        for (var definition : this.resolver.definitions()) {
            if (definition.scope() != BeanScope.SINGLETON
                    || !StartupHook.class.isAssignableFrom(Types.raw(definition.type().type()))) {
                continue;
            }
            var hook = (StartupHook) this.resolve(definition);
            if (!invoked.add(hook)) {
                continue;
            }
            try {
                hook.onStartup();
            } catch (Exception e) {
                throw new BeanCreationException("StartupHook failed for bean: " + definition.key(), e);
            }
        }
    }

    public <T> T get(@NonNull Class<T> type) {
        return this.get(type, "");
    }

    public <T> T get(@NonNull Class<T> type, @NonNull String name) {
        return type.cast(this.get(TypeRef.of(type), name));
    }

    public <T> T get(TypeRef<T> type) {
        return this.get(type, "");
    }

    @SuppressWarnings("unchecked")
    public <T> T get(TypeRef<T> type, String name) {
        this.enter();
        try {
            var descriptor = new DependencyDescriptor(type, name, DependencyKind.DIRECT, new InjectionPoint("get(" + type + ")"));
            return (T) this.resolve(this.resolver.resolve(descriptor).getFirst());
        } finally {
            this.gate.exit();
        }
    }

    private Object resolve(BeanDefinition definition) {
        if (!this.creatingBeans.get().isEmpty()) {
            this.shutdown.recordDependency(this.creatingBeans.get().getLast(), definition.key());
        }
        if (definition.scope() == BeanScope.PROTOTYPE) {
            if (!this.creatingPrototypes.get().add(definition.key())) {
                throw new BeanCreationException("Circular prototype creation: " + definition.key());
            }
            try {
                return this.create(this.plan.beans().get(definition.key()));
            } finally {
                this.creatingPrototypes.get().remove(definition.key());
                if (this.creatingPrototypes.get().isEmpty()) {
                    this.creatingPrototypes.remove();
                }
            }
        }
        var instance = this.singletons.getOrCreate(definition.key(), () -> this.create(this.plan.beans().get(definition.key())));
        if (this.published.add(definition.key())) {
            this.subscriptions.publish(new BeanReadyEvent(definition, instance));
        }
        return instance;
    }

    public Subscription subscribe(Consumer<BeanReadyEvent> listener) {
        this.enter();
        try {
            return this.subscriptions.subscribe(listener);
        } finally {
            this.gate.exit();
        }
    }

    public InitializationPlan report() {
        return this.plan;
    }

    public boolean isClosed() {
        return this.gate.isClosed();
    }

    public <T> T create(Class<T> type) {
        return type.cast(this.create(TypeRef.of(type)));
    }

    @SuppressWarnings("unchecked")
    public <T> T create(TypeRef<T> type) {
        this.enter();
        try {
            var loader = Types.raw(type.type()).getClassLoader();
            var source = new BeanSource(this.transientSourceId, loader == null ? WeftRuntime.class.getClassLoader() : loader);
            var definition = new BeanDefinition(new BeanKey(source.id(), type.toString()), type, "",
                    BeanScope.PROTOTYPE, false, false, source, new ConstructorBeanFactory(type.type()));
            var transientPlan = new PlanBuilder().transientPlan(definition, this.resolver, this.properties, this.plan);
            return (T) this.create(transientPlan);
        } finally {
            this.gate.exit();
        }
    }

    public void inject(Object instance) {
        Objects.requireNonNull(instance, "instance");
        this.enter();
        try {
            var injections = InjectionMetadataReader.readFields(instance.getClass());
            var values = new ArrayList<>();
            for (var field : injections) {
                var keys = new ArrayList<BeanKey>();
                for (var target : this.resolver.resolve(field.dependency())) {
                    keys.add(target.key());
                }
                values.add(this.resolveDependency(new ResolvedDependency(field.dependency(), keys)));
            }
            for (var index = 0; index < injections.size(); index++) {
                var field = injections.get(index).field();
                try {
                    field.set(instance, values.get(index));
                } catch (IllegalAccessException e) {
                    throw new BeanCreationException("Cannot inject " + field, e);
                }
            }
        } finally {
            this.gate.exit();
        }
    }

    private Object create(CreationPlan plan) {
        this.creatingBeans.get().add(plan.definition().key());
        try {
            return this.instantiate(plan);
        } finally {
            this.creatingBeans.get().removeLast();
            if (this.creatingBeans.get().isEmpty()) {
                this.creatingBeans.remove();
            }
        }
    }

    private Object instantiate(CreationPlan plan) {
        var arguments = new Object[plan.arguments().size()];
        for (var index = 0; index < arguments.length; index++) {
            arguments[index] = this.resolveDependency(plan.arguments().get(index));
        }
        var bean = this.creator.create(plan.definition(), arguments);
        Object exposed;
        try {
            if (plan.definition().factory().initialize()) {
                this.initialize(plan, bean);
            }
            exposed = this.process(plan.definition(), bean);
        } catch (RuntimeException | Error e) {
            this.cleaning.set(true);
            try {
                var cleanup = plan.definition().factory().owned() ? this.shutdown.destroyFailed(new ManagedBean(plan, bean)) : null;
                if (cleanup != null) {
                    e.addSuppressed(cleanup);
                }
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            } finally {
                this.cleaning.remove();
            }
            throw e;
        }
        if (plan.definition().scope() == BeanScope.SINGLETON && plan.definition().factory().owned()) {
            this.shutdown.track(new ManagedBean(plan, bean, exposed));
        }
        return exposed;
    }

    private Object process(BeanDefinition definition, Object original) {
        var exposed = original;
        var type = Types.raw(definition.type().type());
        for (var index = 0; index < this.processors.size(); index++) {
            var processor = this.processors.get(index);
            try {
                exposed = processor.process(definition, exposed);
            } catch (Exception e) {
                throw new BeanCreationException("BeanProcessor " + index + " failed for bean: " + definition.key(), e);
            }
            if (!type.isInstance(exposed)) {
                throw new BeanCreationException("Invalid BeanProcessor " + index + " result; expected " + type.getName()
                        + " for bean: " + definition.key());
            }
        }
        return exposed;
    }

    private void initialize(CreationPlan plan, Object bean) {
        PropertiesBinder.bind(plan.properties(), bean);
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
        if (dependency.descriptor().kind() == DependencyKind.VALUE) {
            return PreparedProperty.materialize(dependency.propertyValue());
        }
        if (dependency.descriptor().kind() == DependencyKind.LAZY) {
            var target = this.plan.beans().get(dependency.targets().getFirst()).definition();
            var contract = Types.raw(dependency.descriptor().type().type());
            return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract},
                    new LazyInvocationHandler(contract, this.providerFor(target)));
        }
        if (dependency.descriptor().kind() == DependencyKind.PROVIDER) {
            var target = this.plan.beans().get(dependency.targets().getFirst()).definition();
            return this.providerFor(target);
        }
        var values = new ArrayList<>();
        for (var key : dependency.targets()) {
            values.add(this.resolve(this.plan.beans().get(key).definition()));
        }
        return switch (dependency.descriptor().kind()) {
            case DIRECT -> values.getFirst();
            case OPTIONAL -> values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
            case LIST -> List.copyOf(values);
            case SET -> Collections.unmodifiableSet(new LinkedHashSet<>(values));
            case PROVIDER, LAZY, VALUE ->
                    throw new IllegalStateException("Deferred/value dependency must be resolved separately");
        };
    }

    private Provider<?> providerFor(BeanDefinition definition) {
        var consumer = this.creatingBeans.get().isEmpty() ? null : this.creatingBeans.get().getLast();
        return () -> {
            this.enter();
            try {
                if (consumer != null) {
                    this.shutdown.recordDependency(consumer, definition.key());
                }
                return this.resolve(definition);
            } finally {
                this.gate.exit();
            }
        };
    }

    private void enter() {
        if (this.cleaning.get()) {
            throw new IllegalStateException("Cannot access runtime during cleanup");
        }
        this.gate.enter();
    }

}
