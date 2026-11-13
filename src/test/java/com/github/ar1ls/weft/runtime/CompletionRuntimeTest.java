package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.completion.*;
import com.github.ar1ls.weft.runtime.fixtures.generics.TextConsumer;
import com.github.ar1ls.weft.runtime.fixtures.propertiesadvanced.AdvancedSettings;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CompletionRuntimeTest {

    @Test
    void inheritedFactoryAndInjectedFieldKeepResolvedTypeArguments() throws Exception {
        var type = TextConsumer.class;
        var root = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        var source = new ScanSource(root, new BeanSource("generics", type.getClassLoader()), List.of(type.getPackageName()));
        try (var runtime = Weft.builder().scan(source).start()) {
            TypeRef<Repository<String>> contract = TypeRef.parameterized(Repository.class, TypeRef.of(String.class));
            var repository = runtime.get(contract);
            assertEquals("inherited", repository.value());
            assertSame(repository, runtime.get(TextConsumer.class).getRepository());
            TypeRef<Repository<Integer>> absent = TypeRef.parameterized(Repository.class, TypeRef.of(Integer.class));
            assertThrows(MissingBeanException.class, () -> runtime.get(absent));
        }
    }

    @Test
    void propertiesSupportNestedMutableValuesGenericRecordsMapsAndIndependentPrototypes() throws Exception {
        var type = AdvancedSettings.class;
        var root = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        var source = new ScanSource(root, new BeanSource("settings", type.getClassLoader()), List.of(type.getPackageName()));
        try (var runtime = Weft.builder().scan(source).property("advanced.endpoint.host", "localhost")
                .property("advanced.endpoint.port", "25565").property("advanced.limit.value", "12").start()) {
            var first = runtime.get(AdvancedSettings.class);
            var second = runtime.get(AdvancedSettings.class);
            assertNotSame(first, second);
            assertNotSame(first.endpoint(), second.endpoint());
            assertEquals("localhost", first.endpoint().getHost());
            assertEquals(25565, first.endpoint().getPort());
            assertEquals(12, first.limit().value());
            assertEquals(Map.of("a", 1, "b", 2), first.weights());
            assertEquals(Set.of("one", "two"), first.labels());
            assertThrows(UnsupportedOperationException.class, () -> first.weights().put("c", 3));
        }
        var creations = new AtomicInteger();
        var unrelated = Definitions.supplied("unrelated", TypeRef.of(SimpleBean.class), BeanScope.SINGLETON, false, () -> {
            creations.incrementAndGet();
            return new SimpleBean();
        });
        var failure = assertThrows(PropertyBindingException.class, () -> Weft.builder().scan(source).register(unrelated)
                .property("advanced.endpoint.host", "localhost").property("advanced.endpoint.port", "25565")
                .property("advanced.limit.value", "12").property("advanced.weights", "a=private-value").start());
        assertTrue(failure.getMessage().contains("advanced.weights"));
        assertFalse(failure.getMessage().contains("private-value"));
        assertEquals(0, creations.get());
    }

    @Test
    void bootstrapRejectsOneSourceIdWithDifferentRootsOrLoaders() throws Exception {
        var type = AppSettings.class;
        var root = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        var source = new BeanSource("conflict", type.getClassLoader());
        var first = new ScanSource(root, source, List.of("absent.package"));
        var otherRoot = new ScanSource(root.resolve("different-root"), source, List.of("absent.package"));
        assertThrows(DefinitionException.class, () -> Weft.builder().scan(first).scan(otherRoot).start());
        try (var loader = new URLClassLoader(new URL[0], type.getClassLoader())) {
            var otherLoader = new ScanSource(root, new BeanSource("conflict", loader), List.of("absent.package"));
            assertThrows(DefinitionException.class, () -> Weft.builder().scan(first).scan(otherLoader).start());
        }
    }

    @Test
    void publicBootstrapCombinesSourcesGenericFactoriesAndRecordProperties() throws Exception {
        var root = Path.of(AppSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var source = new ScanSource(root, new BeanSource("completion", AppSettings.class.getClassLoader()),
                List.of(AppSettings.class.getPackageName()));
        try (var runtime = Weft.builder().scan(source).scan(source).register(CompletionConsumer.class)
                .property("message", "configured").property("app.address.port", "25570").start()) {
            var consumer = runtime.get(CompletionConsumer.class);
            assertEquals("configured", consumer.text().value());
            assertEquals(42, consumer.number().get().value());
            assertEquals(new Address("localhost", 25570), consumer.settings().address());
            assertEquals(List.of(1, 2, 3), consumer.settings().levels());
            TypeRef<Repository<String>> text = TypeRef.parameterized(Repository.class, TypeRef.of(String.class));
            assertSame(consumer.text(), runtime.get(text));
            var replay = new ArrayList<String>();
            var subscription = runtime.subscribe(event -> replay.add(event.definition().key().localId()));
            assertEquals(runtime.report().beans().size(), replay.size());
            subscription.close();
            subscription.close();
        }
    }

    @Test
    void createUsesTransientPlanAndInjectDoesNotRunLifecycle() {
        try (var runtime = Weft.builder().register(SimpleBean.class).start()) {
            var first = runtime.create(ExternalConsumer.class);
            var second = runtime.create(ExternalConsumer.class);
            assertNotSame(first, second);
            assertTrue(first.isInitialized());
            assertSame(runtime.get(SimpleBean.class), first.getDependency());
            var external = new ExternalConsumer();
            runtime.inject(external);
            assertSame(runtime.get(SimpleBean.class), external.getDependency());
            assertFalse(external.isInitialized());
        }
    }

    @Test
    void lazyProxyObjectMethodsDoNotCreateTargetAndPrototypeResolvesPerCall() {
        var creations = new AtomicInteger();
        var executions = new AtomicInteger();
        var target = Definitions.supplied("task", TypeRef.of(Runnable.class), BeanScope.PROTOTYPE, true, () -> {
            creations.incrementAndGet();
            return (Runnable) executions::incrementAndGet;
        });
        try (var runtime = Weft.builder().register(target).register(LazyConsumer.class).start()) {
            var task = runtime.get(LazyConsumer.class).task();
            assertTrue(task.toString().contains("WeftLazy"));
            assertEquals(System.identityHashCode(task), task.hashCode());
            assertEquals(task, task);
            assertEquals(0, creations.get());
            task.run();
            task.run();
            assertEquals(2, creations.get());
            assertEquals(2, executions.get());
        }
    }

    @Test
    void concurrentLazySingletonHasOneCreationAndOnePublishedObject() throws Exception {
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var definition = Definitions.supplied("one", TypeRef.of(SimpleBean.class), BeanScope.SINGLETON, true, () -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return new SimpleBean();
        });
        try (var runtime = Weft.builder().register(definition).start(); var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<SimpleBean>>();
            for (var index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> runtime.get(SimpleBean.class)));
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            var first = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (var future : futures) {
                assertSame(first, future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void borrowedInstanceIsNotClosedAndOwnedInstanceIsClosedOnce() {
        var borrowed = new AtomicInteger();
        var owned = new AtomicInteger();
        try (var runtime = Weft.builder()
                .register(Definitions.instance("borrowed", ClosingProbe.class, new ClosingProbe(borrowed), false))
                .register(Definitions.instance("owned", ClosingProbe.class, new ClosingProbe(owned), true)).start()) {
            assertEquals(2, runtime.report().beans().size());
        }
        assertEquals(0, borrowed.get());
        assertEquals(1, owned.get());
    }

    @Test
    void crossThreadCreationCycleFailsWithoutDeadlock() throws Exception {
        var store = new SingletonStore();
        var first = new BeanKey("test", "first");
        var second = new BeanKey("test", "second");
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            var a = executor.submit(() -> store.getOrCreate(first, () -> {
                meet(barrier);
                return store.getOrCreate(second, Object::new);
            }));
            var b = executor.submit(() -> store.getOrCreate(second, () -> {
                meet(barrier);
                return store.getOrCreate(first, Object::new);
            }));
            assertInstanceOf(BeanCreationException.class, assertThrows(ExecutionException.class, () -> a.get(5, TimeUnit.SECONDS)).getCause());
            assertInstanceOf(BeanCreationException.class, assertThrows(ExecutionException.class, () -> b.get(5, TimeUnit.SECONDS)).getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void meet(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
