package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.lifecycle.StartupHook;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.completion.ClosingProbe;
import com.github.ar1ls.weft.runtime.fixtures.completion.ExternalConsumer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GlobalWeftTest {

    @Test
    void globalFacadePublishesAfterHooksAllowsRetryAfterFailureAndCannotRestartAfterClose() {
        assertThrows(IllegalStateException.class, () -> Weft.get(SimpleBean.class));
        var failure = Definitions.supplied("failed", TypeRef.of(StartupHook.class), BeanScope.SINGLETON, false,
                () -> (StartupHook) () -> {
                    throw new IllegalStateException("startup failed");
                });
        assertThrows(BeanCreationException.class, () -> Weft.initialization(Weft.builder().register(failure).build()));
        assertThrows(IllegalStateException.class, () -> Weft.get(SimpleBean.class));

        var hooks = new AtomicInteger();
        var closed = new AtomicInteger();
        StartupHook hook = () -> {
            assertThrows(IllegalStateException.class, () -> Weft.get(SimpleBean.class));
            hooks.incrementAndGet();
        };
        var options = Weft.builder().register(SimpleBean.class)
                .register(Definitions.instance("hook", StartupHook.class, hook, false))
                .register(Definitions.instance("hook-alias", StartupHook.class, hook, false))
                .register(Definitions.instance("resource", ClosingProbe.class, new ClosingProbe(closed), true)).build();
        Weft.initialization(options);
        try {
            assertEquals(1, hooks.get());
            var bean = Weft.get(SimpleBean.class);
            assertSame(bean, Weft.get(SimpleBean.class));
            assertThrows(IllegalStateException.class, () -> Weft.initialization(options));
            assertSame(bean, Weft.create(ExternalConsumer.class).getDependency());
            var external = new ExternalConsumer();
            Weft.inject(external);
            assertSame(bean, external.getDependency());
            assertFalse(external.isInitialized());
            try (var independent = Weft.builder().register(SimpleBean.class).start()) {
                assertNotSame(bean, independent.get(SimpleBean.class));
            }
        } finally {
            Weft.close();
        }
        Weft.close();
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, () -> Weft.get(SimpleBean.class));
        assertThrows(IllegalStateException.class, () -> Weft.initialization(options));
    }

}
