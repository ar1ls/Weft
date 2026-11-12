package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.*;
import com.github.ar1ls.weft.runtime.fixtures.configuration.ProviderMethodConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRuntimeTest {

    @Test
    void createsNewPrototypeOnEachProviderCallWithoutCreatingTargetAtStartup() {
        var calls = new AtomicInteger();
        var target = this.countedTarget(BeanScope.PROTOTYPE, false, calls);
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class), target);
        runtime.start();

        var consumer = runtime.get(ProviderDependentBean.class);
        assertEquals(0, calls.get());
        assertNotSame(consumer.provider().get(), consumer.provider().get());
        assertEquals(2, calls.get());
        assertSame(consumer, runtime.get(ProviderDependentBean.class));
    }

    @Test
    void preservesLazySingletonIdentityAndDefersItsCreation() {
        var calls = new AtomicInteger();
        var target = this.countedTarget(BeanScope.SINGLETON, true, calls);
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class), target);
        runtime.start();
        var provider = runtime.get(ProviderDependentBean.class).provider();

        assertEquals(0, calls.get());
        var first = provider.get();
        assertSame(first, provider.get());
        assertSame(first, runtime.get(SimpleBean.class));
        assertEquals(1, calls.get());
    }

    @Test
    void providerDoesNotMakeEagerTargetLazy() {
        var calls = new AtomicInteger();
        var target = this.countedTarget(BeanScope.SINGLETON, false, calls);
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class), target);

        runtime.start();

        assertEquals(1, calls.get());
        assertSame(runtime.get(SimpleBean.class), runtime.get(ProviderDependentBean.class).provider().get());
    }

    @Test
    void validatesProviderCandidateEvenWhenConsumerIsLazy() {
        var base = BeanDefinitions.of(ProviderDependentBean.class);
        var consumer = BeanDefinitions.of(ProviderDependentBean.class, BeanScope.SINGLETON, true, base.factory());

        var exception = assertThrows(MissingBeanException.class, () -> this.runtimeFor(consumer));

        assertTrue(exception.getMessage().contains(ProviderDependentBean.class.getName()));
        assertTrue(exception.getMessage().contains(SimpleBean.class.getName()));
    }

    @Test
    void allowsDeferredCycleWhenProviderIsCalledAfterOwnerIsReady() {
        var base = BeanDefinitions.of(ProviderCycleTarget.class);
        var target = BeanDefinitions.of(ProviderCycleTarget.class, BeanScope.SINGLETON, true, base.factory());
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderCycleOwner.class), target);
        runtime.start();

        var owner = runtime.get(ProviderCycleOwner.class);
        var resolved = owner.provider().get();

        assertSame(owner, resolved.owner());
        assertSame(resolved, runtime.get(ProviderCycleTarget.class));
    }

    @Test
    void reportsActualCycleWhenConstructorCallsProviderPrematurely() {
        var base = BeanDefinitions.of(PrematureProviderTarget.class);
        var target = BeanDefinitions.of(PrematureProviderTarget.class, BeanScope.SINGLETON, true, base.factory());
        var runtime = this.runtimeFor(BeanDefinitions.of(PrematureProviderOwner.class), target);

        var exception = assertThrows(BeanCreationException.class, runtime::start);

        assertTrue(exception.getCause().getMessage().contains("Circular singleton creation"));
        assertThrows(IllegalStateException.class, () -> runtime.get(PrematureProviderOwner.class));
    }

    @Test
    void permitsProviderCallDuringStartupWhenThereIsNoActualCycle() {
        var calls = new AtomicInteger();
        var target = this.countedTarget(BeanScope.SINGLETON, true, calls);
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderCallingBean.class), target);

        runtime.start();

        assertEquals(1, calls.get());
        assertSame(runtime.get(SimpleBean.class), runtime.get(ProviderCallingBean.class).getDependency());
    }

    @Test
    void injectsProviderIntoBeanMethodWithoutCreatingPrototypeTarget() {
        var calls = new AtomicInteger();
        var source = new BeanSource("test", ProviderMethodConfiguration.class.getClassLoader());
        var definitions = new ArrayList<>(new AnnotationDefinitionReader().read(ProviderMethodConfiguration.class, source));
        definitions.add(this.countedTarget(BeanScope.PROTOTYPE, false, calls));
        var runtime = this.runtimeFor(definitions.toArray(BeanDefinition[]::new));

        runtime.start();

        var provider = runtime.get(ProviderDependentBean.class).provider();
        assertEquals(0, calls.get());
        assertNotSame(provider.get(), provider.get());
        assertEquals(2, calls.get());
    }

    @Test
    void reusesFailedSingletonAttemptThroughProvider() {
        var calls = new AtomicInteger();
        var cause = new IllegalStateException("Provider target probe");
        var factory = new SupplierBeanFactory(() -> {
            calls.incrementAndGet();
            throw cause;
        });
        var target = BeanDefinitions.of(SimpleBean.class, BeanScope.SINGLETON, true, factory);
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class), target);
        runtime.start();
        var provider = runtime.get(ProviderDependentBean.class).provider();

        var first = assertThrows(BeanCreationException.class, provider::get);
        var second = assertThrows(BeanCreationException.class, provider::get);

        assertSame(first, second);
        assertSame(cause, first.getCause());
        assertEquals(1, calls.get());
    }

    @Test
    void retriesPrototypeWithNewCreationAttemptAfterFailure() {
        var calls = new AtomicInteger();
        var factory = new SupplierBeanFactory(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("First prototype attempt");
            }
            return new SimpleBean();
        });
        var target = BeanDefinitions.of(SimpleBean.class, BeanScope.PROTOTYPE, false, factory);
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class), target);
        runtime.start();
        var provider = runtime.get(ProviderDependentBean.class).provider();

        assertThrows(BeanCreationException.class, provider::get);
        assertNotSame(provider.get(), provider.get());
        assertEquals(3, calls.get());
    }

    @Test
    void rejectsProviderCallsFromAnotherThread() {
        var calls = new AtomicInteger();
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class),
                this.countedTarget(BeanScope.SINGLETON, true, calls));
        runtime.start();
        var provider = runtime.get(ProviderDependentBean.class).provider();

        var exception = CompletableFuture.supplyAsync(() ->
                assertThrows(IllegalStateException.class, provider::get)).join();

        assertTrue(exception.getMessage().contains("owner thread"));
        assertEquals(0, calls.get());
    }

    @Test
    void leakedProviderCannotCreateBeansAfterFailedStartup() {
        var leaked = new AtomicReference<Provider<?>>();
        var dependency = new DependencyDescriptor(TypeRef.of(SimpleBean.class), "", DependencyKind.PROVIDER,
                new InjectionPoint("leaked provider"));
        var factory = new SupplierBeanFactory(List.of(dependency), arguments -> {
            leaked.set((Provider<?>) arguments[0]);
            throw new IllegalStateException("Startup probe");
        });
        var calls = new AtomicInteger();
        var runtime = this.runtimeFor(BeanDefinitions.of(ProviderDependentBean.class, factory),
                this.countedTarget(BeanScope.SINGLETON, true, calls));

        assertThrows(BeanCreationException.class, runtime::start);

        assertNotNull(leaked.get());
        assertThrows(IllegalStateException.class, () -> leaked.get().get());
        assertEquals(0, calls.get());
    }

    private BeanDefinition countedTarget(BeanScope scope, boolean lazy, AtomicInteger calls) {
        var factory = new SupplierBeanFactory(() -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        return BeanDefinitions.of(SimpleBean.class, scope, lazy, factory);
    }

    private WeftRuntime runtimeFor(BeanDefinition... definitions) {
        var registry = new BeanRegistry();
        for (var definition : definitions) {
            registry.register(definition);
        }
        return new WeftRuntime(registry);
    }

}
