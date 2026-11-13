package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.lifecycle.StartupHook;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.completion.ClosingProbe;
import com.github.ar1ls.weft.runtime.fixtures.fallback.FallbackConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.fallbackconflict.OtherFallbackConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapLifecycleTest {

    @Test
    void competingConfigurationFallbacksFailBeforeOwnerDependenciesAndIgnoreScanOrder() throws Exception {
        var root = Path.of(FallbackConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var packages = List.of(FallbackConfiguration.class.getPackageName(), OtherFallbackConfiguration.class.getPackageName());
        String message = null;
        for (var order : List.of(packages, packages.reversed())) {
            var source = new ScanSource(root, new BeanSource("fallback-conflict", FallbackConfiguration.class.getClassLoader()), order);
            var failure = assertThrows(DefinitionException.class, () -> Weft.builder().scan(source).start());
            assertTrue(failure.getMessage().contains("Conflicting configuration fallbacks"));
            assertTrue(failure.getMessage().contains(FallbackConfiguration.class.getName()));
            assertTrue(failure.getMessage().contains(OtherFallbackConfiguration.class.getName()));
            if (message != null) {
                assertEquals(message, failure.getMessage());
            }
            message = failure.getMessage();
        }
    }

    @Test
    void configurationFallbackDropsOwnerAndMethodsBeforeDependencyValidation() throws Exception {
        var root = Path.of(FallbackConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var source = new ScanSource(root, new BeanSource("fallback", FallbackConfiguration.class.getClassLoader()),
                List.of(FallbackConfiguration.class.getPackageName()));
        Runnable ordinary = () -> {};
        try (var runtime = Weft.builder().scan(source).register(Definitions.instance("task", Runnable.class, ordinary, false)).start()) {
            assertSame(ordinary, runtime.get(Runnable.class));
            assertEquals(1, runtime.report().beans().size());
        }
        try (var runtime = Weft.builder().scan(source).register(SimpleBean.class).start()) {
            assertNotNull(runtime.get(FallbackConfiguration.class));
            assertNotNull(runtime.get(Runnable.class));
        }
    }

    @Test
    void startupHookFailureRollsBackAndPreservesCause() {
        var closed = new AtomicInteger();
        var cause = new IOException("hook failure");
        var resource = Definitions.supplied("resource", TypeRef.of(ClosingProbe.class), BeanScope.SINGLETON, false, () -> new ClosingProbe(closed));
        var hook = Definitions.supplied("hook", TypeRef.of(StartupHook.class), BeanScope.SINGLETON, true,
                () -> (StartupHook) () -> {
                    throw cause;
                });
        var failure = assertThrows(BeanCreationException.class, () -> Weft.builder().register(resource).register(hook).start());
        assertSame(cause, failure.getCause());
        assertEquals(1, closed.get());
    }

    @Test
    void contributorAndLifecycleReplayUseOneRegistry() {
        var definition = Definitions.supplied("lazy", TypeRef.of(SimpleBean.class), BeanScope.SINGLETON, true, SimpleBean::new);
        try (var runtime = Weft.builder().contributor(() -> List.of(definition)).start()) {
            var events = new ArrayList<Object>();
            var subscription = runtime.subscribe(event -> events.add(event.instance()));
            assertTrue(events.isEmpty());
            var bean = runtime.get(SimpleBean.class);
            assertEquals(List.of(bean), events);
            runtime.get(SimpleBean.class);
            assertEquals(1, events.size());
            subscription.close();
            var replay = new ArrayList<Object>();
            try (var ignored = runtime.subscribe(event -> replay.add(event.instance()))) {
                assertEquals(List.of(bean), replay);
            }
        }
    }

    @Test
    void concurrentSingletonFailureIsShared() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var definition = Definitions.supplied("failure", TypeRef.of(SimpleBean.class), BeanScope.SINGLETON, true, () -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            throw new IllegalStateException("same failure");
        });
        var runtime = Weft.builder().register(definition).start();
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            var first = executor.submit(() -> runtime.get(SimpleBean.class));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> runtime.get(SimpleBean.class));
            release.countDown();
            var a = assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS)).getCause();
            var b = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS)).getCause();
            assertSame(a, b);
            assertSame(a, assertThrows(BeanCreationException.class, () -> runtime.get(SimpleBean.class)));
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            runtime.close();
        }
    }

    @Test
    void closeWaitsForActiveCreationThenClosesTheResultOnce() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closingStarted = new CountDownLatch(1);
        var closed = new AtomicInteger();
        var definition = Definitions.supplied("resource", TypeRef.of(ClosingProbe.class), BeanScope.SINGLETON, true, () -> {
            entered.countDown();
            await(release);
            return new ClosingProbe(closed);
        });
        var runtime = Weft.builder().register(definition).start();
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            var creation = executor.submit(() -> runtime.get(ClosingProbe.class));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var closing = executor.submit(() -> {
                closingStarted.countDown();
                runtime.close();
            });
            assertTrue(closingStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            assertEquals(0, closed.get());
            release.countDown();
            assertNotNull(creation.get(5, TimeUnit.SECONDS));
            closing.get(5, TimeUnit.SECONDS);
            assertEquals(1, closed.get());
            assertTrue(runtime.isClosed());
            runtime.close();
            assertEquals(1, closed.get());
            assertThrows(IllegalStateException.class, () -> runtime.get(ClosingProbe.class));
        } finally {
            release.countDown();
            executor.shutdownNow();
            runtime.close();
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

}
