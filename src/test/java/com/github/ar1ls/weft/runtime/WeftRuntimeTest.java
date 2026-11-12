package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.TestBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.ThrowingConstructorBean;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WeftRuntimeTest {

    @Test
    void sameInstanceForSingleton() {
        var calls = new AtomicInteger();
        var factory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var runtime = this.runtimeFor(BeanDefinitions.of(SimpleBean.class, factory));

        runtime.start();

        assertEquals(1, calls.get());
        assertSame(runtime.get(SimpleBean.class), runtime.get(SimpleBean.class));
        assertEquals(1, calls.get());
    }

    @Test
    void missingBeanReportsRequestedType() {
        var runtime = this.runtimeFor();
        runtime.start();

        var exception = assertThrows(MissingBeanException.class, () -> runtime.get(SimpleBean.class));

        assertTrue(exception.getMessage().contains(SimpleBean.class.getName()));
    }

    @Test
    void runtimesDoNotShareSingletons() {
        var definition = BeanDefinitions.of(SimpleBean.class);
        var first = this.runtimeFor(definition);
        var second = this.runtimeFor(definition);
        first.start();
        second.start();

        assertNotSame(first.get(SimpleBean.class), second.get(SimpleBean.class));
    }

    @Test
    void createsLazySingletonOnFirstRequest() {
        var calls = new AtomicInteger();
        var factory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var definition = BeanDefinitions.of(SimpleBean.class, BeanScope.SINGLETON, true, factory);
        var runtime = this.runtimeFor(definition);

        runtime.start();

        assertEquals(0, calls.get());
        assertSame(runtime.get(SimpleBean.class), runtime.get(SimpleBean.class));
        assertEquals(1, calls.get());
    }

    @Test
    void createsPrototypeOnEachRequestWithoutCreatingItAtStartup() {
        var calls = new AtomicInteger();
        var factory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var definition = BeanDefinitions.of(SimpleBean.class, BeanScope.PROTOTYPE, false, factory);
        var runtime = this.runtimeFor(definition);

        runtime.start();

        assertEquals(0, calls.get());
        assertNotSame(runtime.get(SimpleBean.class), runtime.get(SimpleBean.class));
        assertEquals(2, calls.get());
    }

    @Test
    void preservesLazySingletonFailureWithoutRetrying() {
        var calls = new AtomicInteger();
        var cause = new IllegalStateException("Singleton probe");
        var factory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            throw cause;
        });
        var definition = BeanDefinitions.of(SimpleBean.class, BeanScope.SINGLETON, true, factory);
        var runtime = this.runtimeFor(definition);
        runtime.start();

        var first = assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class));
        var second = assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class));

        assertSame(first, second);
        assertSame(cause, first.getCause());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsRequestsAndRestartAfterFailedStartup() {
        var runtime = this.runtimeFor(BeanDefinitions.of(ThrowingConstructorBean.class));

        assertThrows(BeanCreationException.class, runtime::start);
        assertThrows(IllegalStateException.class, () -> runtime.get(ThrowingConstructorBean.class));
        assertThrows(IllegalStateException.class, runtime::start);
    }

    @Test
    void rejectsRequestBeforeStartupAndRepeatedStartup() {
        var runtime = this.runtimeFor(BeanDefinitions.of(SimpleBean.class));

        assertThrows(IllegalStateException.class, () -> runtime.get(SimpleBean.class));

        runtime.start();

        assertThrows(IllegalStateException.class, runtime::start);
    }

    @Test
    void freezesRegistryWhenRuntimeIsPrepared() {
        var registry = new BeanRegistry();
        new WeftRuntime(registry);

        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(BeanDefinitions.of(SimpleBean.class)));
    }

    @Test
    void rejectsMultipleDefinitionsForSameType() {
        var first = BeanDefinitions.of(SimpleBean.class);
        var key = new BeanKey(first.key().sourceId(), "second-simple-bean");
        var second = new BeanDefinition(key, first.type(), "", first.scope(), false, false,
                first.source(), first.factory());

        var exception = assertThrows(DefinitionException.class, () -> this.runtimeFor(first, second));

        assertTrue(exception.getMessage().contains(first.key().toString()));
        assertTrue(exception.getMessage().contains(second.key().toString()));
    }

    @Test
    void rejectsUnsupportedDependenciesBeforeCreatingAnyBean() {
        var calls = new AtomicInteger();
        var firstFactory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var dependency = new DependencyDescriptor(TypeRef.of(SimpleBean.class), "", DependencyKind.DIRECT,
                new InjectionPoint("test factory argument"));
        var secondFactory = new TestBeanFactory(List.of(dependency), Object::new);

        assertThrows(DefinitionException.class, () -> this.runtimeFor(
                BeanDefinitions.of(SimpleBean.class, firstFactory),
                BeanDefinitions.of(Object.class, secondFactory)));
        assertEquals(0, calls.get());
    }

    @Test
    void reportsReentrantSingletonCreationInsteadOfRecursing() {
        var holder = new WeftRuntime[1];
        var factory = new TestBeanFactory(List.of(), () -> holder[0].get(SimpleBean.class));
        var definition = BeanDefinitions.of(SimpleBean.class, BeanScope.SINGLETON, true, factory);
        var runtime = this.runtimeFor(definition);
        holder[0] = runtime;
        runtime.start();

        var exception = assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class));

        assertTrue(exception.getCause().getMessage().contains("Circular singleton creation"));
        assertSame(exception, assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class)));
    }

    @Test
    void reportsReentrantPrototypeCreationInsteadOfRecursing() {
        var holder = new WeftRuntime[1];
        var factory = new TestBeanFactory(List.of(), () -> holder[0].get(SimpleBean.class));
        var definition = BeanDefinitions.of(SimpleBean.class, BeanScope.PROTOTYPE, false, factory);
        var runtime = this.runtimeFor(definition);
        holder[0] = runtime;
        runtime.start();

        var exception = assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class));

        assertTrue(exception.getCause().getMessage().contains("Circular prototype creation"));
    }

    @Test
    void rejectsRequestsFromAnotherThread() {
        var runtime = this.runtimeFor(BeanDefinitions.of(SimpleBean.class));
        runtime.start();

        var exception = CompletableFuture.supplyAsync(() ->
                assertThrows(IllegalStateException.class, () -> runtime.get(SimpleBean.class))).join();

        assertTrue(exception.getMessage().contains("owner thread"));
    }

    private WeftRuntime runtimeFor(BeanDefinition... definitions) {
        var registry = new BeanRegistry();
        for (var definition : definitions) {
            registry.register(definition);
        }
        return new WeftRuntime(registry);
    }

}
