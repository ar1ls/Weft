package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.ShutdownException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.*;
import com.github.ar1ls.weft.spi.BeanFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShutdownRuntimeTest {

    @Test
    void closesConsumerBeforeDependencyAndDoesNotRepeatClose() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ShutdownConsumer.class, new SupplierBeanFactory(
                        List.of(this.dependency(ShutdownResource.class, DependencyKind.DIRECT)),
                        args -> new ShutdownConsumer((ShutdownResource) args[0], events))),
                this.primaryResource(BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new ShutdownResource("resource", events)))
        );
        runtime.start();

        runtime.close();
        runtime.close();

        assertEquals(List.of("consumer:destroy", "consumer:close", "resource:destroy", "resource:close"), events);
        assertThrows(IllegalStateException.class, () -> runtime.get(ShutdownConsumer.class));
        assertThrows(IllegalStateException.class, runtime::start);
    }

    @Test
    void closesIndependentBeansInReverseCreationOrderAndAnnotatedCloseOnce() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(
                this.primaryResource(new SupplierBeanFactory(() -> new ShutdownResource("resource", events))),
                BeanDefinitions.of(AnnotatedCloseResource.class, new SupplierBeanFactory(() -> new AnnotatedCloseResource(events)))
        );
        runtime.start();

        runtime.close();

        assertEquals(List.of("annotated:close", "resource:destroy", "resource:close"), events);
    }

    @Test
    void destroysChildBeforeParentAndThenClosesObject() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(BeanDefinitions.of(ShutdownChild.class, new SupplierBeanFactory(() -> new ShutdownChild(events))));
        runtime.start();

        runtime.close();

        assertEquals(List.of("child:destroy", "base:destroy", "base:close"), events);
    }

    @Test
    void singletonAliasClosesSharedInstanceOnceUsingConcreteLifecycle() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ShutdownConsumer.class, new SupplierBeanFactory(
                        List.of(this.dependency(AutoCloseable.class, DependencyKind.DIRECT)),
                        args -> new ShutdownConsumer((ShutdownResource) args[0], events))),
                BeanDefinitions.of(AutoCloseable.class, new SupplierBeanFactory(
                        List.of(this.dependency(ShutdownResource.class, DependencyKind.DIRECT)), args -> args[0])),
                this.primaryResource(new SupplierBeanFactory(() -> new ShutdownResource("resource", events)))
        );
        runtime.start();

        assertSame(runtime.get(ShutdownResource.class), runtime.get(AutoCloseable.class));
        runtime.close();

        assertEquals(List.of("consumer:destroy", "consumer:close", "resource:destroy", "resource:close"), events);
    }

    @Test
    void usedProviderKeepsLateCreatedDependencyAliveUntilConsumerIsClosed() {
        var events = new ArrayList<String>();
        var runtime = this.providerRuntime(events);
        runtime.start();
        var provider = runtime.get(ProviderShutdownConsumer.class).getResource();
        provider.get();

        runtime.close();

        assertEquals(List.of("provider:destroy", "provider:close", "resource:destroy", "resource:close"), events);
        assertThrows(IllegalStateException.class, provider::get);
    }

    @Test
    void unusedProviderDoesNotCreateTargetDuringShutdown() {
        var events = new ArrayList<String>();
        var calls = new AtomicInteger();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ProviderShutdownConsumer.class, new SupplierBeanFactory(() -> new ProviderShutdownConsumer(events))),
                this.primaryResource(BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> {
                    calls.incrementAndGet();
                    return new ShutdownResource("resource", events);
                }))
        );
        runtime.start();

        runtime.close();

        assertEquals(0, calls.get());
        assertEquals(List.of("provider:destroy", "provider:close"), events);
    }

    @Test
    void tracksDynamicGetInsideLazyFactory() {
        var events = new ArrayList<String>();
        var owner = new AtomicReference<WeftRuntime>();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ShutdownConsumer.class, BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new ShutdownConsumer(owner.get().get(ShutdownResource.class), events))),
                this.primaryResource(BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new ShutdownResource("resource", events)))
        );
        owner.set(runtime);
        runtime.start();
        runtime.get(ShutdownConsumer.class);

        runtime.close();

        assertEquals(List.of("consumer:destroy", "consumer:close", "resource:destroy", "resource:close"), events);
    }

    @Test
    void followsSingletonDependenciesThroughUnmanagedPrototype() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ShutdownConsumer.class, new SupplierBeanFactory(
                        List.of(this.dependency(ProviderShutdownConsumer.class, DependencyKind.DIRECT)),
                        args -> new ShutdownConsumer((ProviderShutdownConsumer) args[0], events))),
                BeanDefinitions.of(ProviderShutdownConsumer.class, BeanScope.PROTOTYPE, false,
                        new SupplierBeanFactory(() -> new ProviderShutdownConsumer(events))),
                this.primaryResource(BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new ShutdownResource("resource", events)))
        );
        runtime.start();
        var bridge = (ProviderShutdownConsumer) runtime.get(ShutdownConsumer.class).getResource();
        bridge.getResource().get();

        runtime.close();

        assertEquals(List.of("consumer:destroy", "consumer:close", "resource:destroy", "resource:close"), events);
    }

    @Test
    void ordersConsumersBeforeProviderCycleAndUsesReverseCreationOrderInsideCycle() {
        var events = new ArrayList<String>();
        var backReference = new AtomicReference<Provider<?>>();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ShutdownConsumer.class, new SupplierBeanFactory(
                        List.of(this.dependency(ProviderShutdownConsumer.class, DependencyKind.DIRECT)),
                        args -> new ShutdownConsumer((ProviderShutdownConsumer) args[0], events))),
                BeanDefinitions.of(ProviderShutdownConsumer.class, new SupplierBeanFactory(() -> new ProviderShutdownConsumer(events))),
                this.primaryResource(BeanScope.SINGLETON, true, new SupplierBeanFactory(
                        List.of(this.dependency(ProviderShutdownConsumer.class, DependencyKind.PROVIDER)), args -> {
                    backReference.set((Provider<?>) args[0]);
                    return new ShutdownResource("resource", events);
                }))
        );
        runtime.start();
        runtime.get(ProviderShutdownConsumer.class).getResource().get();
        backReference.get().get();

        runtime.close();

        assertEquals(List.of("consumer:destroy", "consumer:close", "resource:destroy", "resource:close",
                "provider:destroy", "provider:close"), events);
    }

    @Test
    void successfulPrototypeRemainsOwnedByCaller() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(this.primaryResource(BeanScope.PROTOTYPE, false,
                new SupplierBeanFactory(() -> new ShutdownResource("prototype", events))));
        runtime.start();
        var bean = runtime.get(ShutdownResource.class);

        runtime.close();

        assertEquals(List.of(), events);
        bean.close();
        assertEquals(List.of("prototype:close"), events);
    }

    @Test
    void continuesAfterHookAndCloseErrorsAndDoesNotRetryShutdown() {
        var events = new ArrayList<String>();
        var hookFailure = new IllegalStateException("destroy failed");
        var closeFailure = new AssertionError("close failed");
        var bean = new ShutdownResource("resource", events);
        bean.setDestructionFailure(hookFailure);
        bean.setCloseFailure(closeFailure);
        var runtime = this.runtimeFor(
                BeanDefinitions.of(AnnotatedCloseResource.class, new SupplierBeanFactory(() -> new AnnotatedCloseResource(events))),
                this.primaryResource(new SupplierBeanFactory(() -> bean))
        );
        runtime.start();

        var exception = assertThrows(ShutdownException.class, runtime::close);
        runtime.close();

        assertEquals(List.of("resource:destroy", "resource:close", "annotated:close"), events);
        assertTrue(this.containsCause(exception, hookFailure));
        assertTrue(this.containsCause(exception, closeFailure));
        assertThrows(IllegalStateException.class, () -> runtime.get(ShutdownResource.class));
    }

    @Test
    void rollsBackFailedObjectAndPreviouslyReadySingletonsPreservingStartupFailure() {
        var events = new ArrayList<String>();
        var cause = new IllegalStateException("initialization failed");
        var cleanupFailure = new IllegalArgumentException("failed object cleanup");
        var rollbackFailure = new IllegalArgumentException("ready object cleanup");
        var bad = new ShutdownResource("bad", events);
        bad.setInitializationFailure(cause);
        bad.setDestructionFailure(cleanupFailure);
        var ready = new ShutdownChild(events);
        ready.setDestructionFailure(rollbackFailure);
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ShutdownChild.class, new SupplierBeanFactory(() -> ready)),
                this.primaryResource(new SupplierBeanFactory(() -> bad))
        );

        var exception = assertThrows(BeanCreationException.class, runtime::start);
        runtime.close();

        assertSame(cause, exception.getCause());
        assertTrue(this.containsCause(exception, cleanupFailure));
        assertTrue(this.containsCause(exception, rollbackFailure));
        assertEquals(List.of("bad:destroy", "bad:close", "child:destroy", "base:destroy", "base:close"), events);
        assertThrows(IllegalStateException.class, () -> runtime.get(ShutdownChild.class));
    }

    @Test
    void cleansFailedLazySingletonOnceWithoutStoppingRunningRuntime() {
        var events = new ArrayList<String>();
        var bad = new ShutdownResource("bad", events);
        bad.setInitializationFailure(new IllegalStateException("lazy initialization failed"));
        var runtime = this.runtimeFor(
                this.primaryResource(BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> bad)),
                BeanDefinitions.of(AnnotatedCloseResource.class, new SupplierBeanFactory(() -> new AnnotatedCloseResource(events)))
        );
        runtime.start();

        var first = assertThrows(BeanCreationException.class, () -> runtime.get(ShutdownResource.class));
        var second = assertThrows(BeanCreationException.class, () -> runtime.get(ShutdownResource.class));
        assertNotNull(runtime.get(AnnotatedCloseResource.class));
        runtime.close();

        assertSame(first, second);
        assertEquals(List.of("bad:destroy", "bad:close", "annotated:close"), events);
    }

    @Test
    void cleansEachFailedPrototypeAttempt() {
        var events = new ArrayList<String>();
        var runtime = this.runtimeFor(this.primaryResource(BeanScope.PROTOTYPE, false,
                new SupplierBeanFactory(() -> {
                    var bean = new ShutdownResource("bad", events);
                    bean.setInitializationFailure(new IllegalStateException("prototype initialization failed"));
                    return bean;
                })));
        runtime.start();

        assertThrows(BeanCreationException.class, () -> runtime.get(ShutdownResource.class));
        assertThrows(BeanCreationException.class, () -> runtime.get(ShutdownResource.class));
        runtime.close();

        assertEquals(List.of("bad:destroy", "bad:close", "bad:destroy", "bad:close"), events);
    }

    @Test
    void cleansConstructedConsumerWhenFieldDependencyFails() {
        var events = new ArrayList<String>();
        var bad = new ShutdownResource("bad", events);
        bad.setInitializationFailure(new IllegalStateException("field dependency failed"));
        var runtime = this.runtimeFor(
                BeanDefinitions.of(FieldShutdownConsumer.class, BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new FieldShutdownConsumer(events))),
                this.primaryResource(BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> bad)),
                BeanDefinitions.of(AnnotatedCloseResource.class, new SupplierBeanFactory(() -> new AnnotatedCloseResource(events)))
        );
        runtime.start();

        assertThrows(BeanCreationException.class, () -> runtime.get(FieldShutdownConsumer.class));
        assertNotNull(runtime.get(AnnotatedCloseResource.class));
        runtime.close();

        assertEquals(List.of("bad:destroy", "bad:close", "field:destroy", "field:close", "annotated:close"), events);
    }

    @Test
    void shutdownRejectsGetAndProviderCallsAndAllowsReentrantClose() {
        var events = new ArrayList<String>();
        var bean = new ProviderShutdownConsumer(events);
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ProviderShutdownConsumer.class, new SupplierBeanFactory(() -> bean)),
                this.primaryResource(BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new ShutdownResource("resource", events)))
        );
        bean.setBeforeDestroy(() -> {
            assertThrows(IllegalStateException.class, () -> runtime.get(ProviderShutdownConsumer.class));
            assertThrows(IllegalStateException.class, () -> bean.getResource().get());
            runtime.close();
        });
        runtime.start();

        runtime.close();

        assertEquals(List.of("provider:destroy", "provider:close"), events);
    }

    @Test
    void cannotCloseWhileLazyFactoryIsRunning() {
        var owner = new AtomicReference<WeftRuntime>();
        var runtime = this.runtimeFor(BeanDefinitions.of(SimpleBean.class, BeanScope.SINGLETON, true,
                new SupplierBeanFactory(() -> {
                    owner.get().close();
                    return new SimpleBean();
                })));
        owner.set(runtime);
        runtime.start();

        var exception = assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        runtime.close();
    }

    @Test
    void rejectsInvalidDestroyMetadataBeforeLazyFactoryRuns() {
        for (var type : List.of(InvalidDestroyBean.class, MultipleDestroyBean.class)) {
            var calls = new AtomicInteger();
            var definition = BeanDefinitions.of(type, BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> {
                calls.incrementAndGet();
                return null;
            }));

            var exception = assertThrows(DefinitionException.class, () -> this.runtimeFor(definition));

            assertTrue(exception.getMessage().contains("PreDestroy"));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void canCloseBeforeStartWithoutCreatingAnything() {
        var calls = new AtomicInteger();
        var runtime = this.runtimeFor(BeanDefinitions.of(SimpleBean.class, new SupplierBeanFactory(() -> {
            calls.incrementAndGet();
            return new SimpleBean();
        })));

        runtime.close();
        runtime.close();

        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, runtime::start);
    }

    @Test
    void allowsCloseFromAnotherThread() throws Exception {
        var runtime = this.runtimeFor();
        runtime.start();
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                runtime.close();
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        thread.join(1000);

        assertFalse(thread.isAlive());
        assertNull(failure.get());
        assertTrue(runtime.isClosed());
        runtime.close();
    }

    private WeftRuntime providerRuntime(List<String> events) {
        return this.runtimeFor(
                BeanDefinitions.of(ProviderShutdownConsumer.class, new SupplierBeanFactory(() -> new ProviderShutdownConsumer(events))),
                this.primaryResource(BeanScope.SINGLETON, true,
                        new SupplierBeanFactory(() -> new ShutdownResource("resource", events)))
        );
    }

    private DependencyDescriptor dependency(Class<?> type, DependencyKind kind) {
        return new DependencyDescriptor(TypeRef.of(type), "", kind, new InjectionPoint("shutdown fixture"));
    }

    private BeanDefinition primaryResource(BeanFactory factory) {
        return this.primaryResource(BeanScope.SINGLETON, false, factory);
    }

    private BeanDefinition primaryResource(BeanScope scope, boolean lazy, BeanFactory factory) {
        var base = BeanDefinitions.of(ShutdownResource.class, scope, lazy, factory);
        return new BeanDefinition(base.key(), base.type(), base.name(), base.scope(), base.lazy(), true, base.source(), base.factory());
    }

    private boolean containsCause(Throwable failure, Throwable expected) {
        if (failure == expected) {
            return true;
        }
        if (failure.getCause() != null && this.containsCause(failure.getCause(), expected)) {
            return true;
        }
        for (var suppressed : failure.getSuppressed()) {
            if (this.containsCause(suppressed, expected)) {
                return true;
            }
        }
        return false;
    }

    private WeftRuntime runtimeFor(BeanDefinition... definitions) {
        var registry = new BeanRegistry();
        for (var definition : definitions) {
            registry.register(definition);
        }
        return new WeftRuntime(registry);
    }

}
