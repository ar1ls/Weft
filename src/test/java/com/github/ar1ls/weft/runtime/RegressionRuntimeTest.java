package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.WeftBuilder;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.regression.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RegressionRuntimeTest {

    @Test
    void interfaceFactoryInitializesAndDestroysTheActualImplementationOnce() {
        var events = new ArrayList<String>();
        var trace = new LifecycleTrace(events);
        var builder = this.builder(WalletConfiguration.class).register(SimpleBean.class)
                .register(Definitions.instance("trace", LifecycleTrace.class, trace, false));
        WeftRuntime closed;
        try (var runtime = builder.start()) {
            closed = runtime;
            var wallet = runtime.get(Wallet.class);
            var implementation = assertInstanceOf(WalletImpl.class, wallet);
            assertSame(runtime.get(SimpleBean.class), implementation.getDependency());
            assertEquals("ready", wallet.status());
            assertSame(wallet, runtime.get(Wallet.class));
            assertEquals(List.of("init:true:ready"), events);
            assertThrows(MissingBeanException.class, () -> runtime.get(WalletImpl.class));
        }
        closed.close();
        assertEquals(List.of("init:true:ready", "destroy", "close"), events);
    }

    @Test
    void failedPreparationCleansTheActualImplementationWithoutCallingPostConstruct() {
        var events = new ArrayList<String>();
        var builder = this.builder(WalletConfiguration.class)
                .register(Definitions.instance("trace", LifecycleTrace.class, new LifecycleTrace(events), false));
        assertThrows(MissingBeanException.class, builder::start);
        assertEquals(List.of("destroy", "close"), events);
    }

    @Test
    void inheritedConfigurationsUseTheirExactOwnersDespitePrimary() {
        try (var runtime = this.builder(BaseConfiguration.class, ChildConfiguration.class).start()) {
            var base = runtime.get(OwnerIdentity.class, "base").owner();
            var child = runtime.get(OwnerIdentity.class, "child").owner();
            assertEquals(BaseConfiguration.class, base.getClass());
            assertEquals(ChildConfiguration.class, child.getClass());
            assertNotSame(base, child);
            assertSame(child, runtime.get(BaseConfiguration.class));
        }
    }

    @Test
    void recursiveUnmanagedCreateFailsWithAChainBeforeASecondConstructorInvocation() {
        var reference = new AtomicReference<WeftRuntime>();
        var calls = new AtomicInteger();
        Runnable callback = () -> {
            if (calls.incrementAndGet() > 2) {
                throw new AssertionError("Unmanaged recursion was not intercepted");
            }
            reference.get().create(RecursiveUnmanaged.class);
        };
        try (var runtime = Weft.builder().register(Definitions.instance("callback", Runnable.class, callback, false)).start()) {
            reference.set(runtime);
            var failure = assertThrows(BeanCreationException.class, () -> runtime.create(RecursiveUnmanaged.class));
            Throwable root = failure;
            while (root.getCause() != null) {
                assertFalse(root instanceof StackOverflowError);
                root = root.getCause();
            }
            assertInstanceOf(BeanCreationException.class, root);
            assertTrue(root.getMessage().contains("Circular bean creation"));
            var name = RecursiveUnmanaged.class.getName();
            assertNotEquals(root.getMessage().indexOf(name), root.getMessage().lastIndexOf(name));
            assertEquals(1, calls.get());
        }
    }

    private WeftBuilder builder(Class<?>... configurations) {
        var builder = Weft.builder();
        var reader = new AnnotationDefinitionReader();
        var source = new BeanSource("regression", this.getClass().getClassLoader());
        for (var type : configurations) {
            for (var definition : reader.read(type, source)) {
                builder.register(definition);
            }
        }
        return builder;
    }

}
