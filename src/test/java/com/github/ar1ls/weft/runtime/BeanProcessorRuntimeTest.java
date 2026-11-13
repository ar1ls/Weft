package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.processor.ProcessorConsumer;
import com.github.ar1ls.weft.runtime.fixtures.processor.ProcessorTarget;
import com.github.ar1ls.weft.runtime.fixtures.processor.ProcessorWrapper;
import com.github.ar1ls.weft.spi.BeanFactory;
import com.github.ar1ls.weft.spi.BeanProcessor;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BeanProcessorRuntimeTest {

    @Test
    void chainsProcessorsAfterInitializationAndPublishesOneWrapperEverywhere() {
        var events = new ArrayList<String>();
        var original = new ProcessorTarget(events);
        var processors = createProcessors(original, events);
        var registry = this.registryFor(BeanScope.SINGLETON, false, new SupplierBeanFactory(() -> original));
        registry.register(BeanDefinitions.of(ProcessorConsumer.class));
        ProcessorTarget exposed;
        try (var runtime = new WeftRuntime(registry, this.properties(), processors)) {
            processors.clear();
            runtime.start();
            exposed = runtime.get(ProcessorTarget.class);
            assertNotSame(original, exposed);
            assertSame(exposed, runtime.get(ProcessorTarget.class));
            assertEquals("second:first:configured", exposed.message());
            var consumer = runtime.get(ProcessorConsumer.class);
            assertSame(exposed, consumer.direct());
            assertSame(exposed, consumer.provider().get());
            assertSame(exposed, consumer.all().getFirst());
            assertSame(exposed, consumer.optional().orElseThrow());
            assertNull(exposed.getDependency());
            assertTrue(exposed.getEvents().isEmpty());
        }
        assertEquals(List.of("init:configured:true", "first", "second", "destroy", "close"), events);
        assertTrue(exposed.getEvents().isEmpty());
    }

    private static @NonNull ArrayList<BeanProcessor> createProcessors(ProcessorTarget original, ArrayList<String> events) {
        var processors = new ArrayList<BeanProcessor>();
        processors.add((definition, bean) -> {
            if (definition.type().type() != ProcessorTarget.class) {
                return bean;
            }
            assertSame(original, bean);
            assertEquals(List.of("init:configured:true"), events);
            events.add("first");
            return new ProcessorWrapper((ProcessorTarget) bean, "first:");
        });
        processors.add((definition, bean) -> {
            if (definition.type().type() != ProcessorTarget.class) {
                return bean;
            }
            assertInstanceOf(ProcessorWrapper.class, bean);
            events.add("second");
            return new ProcessorWrapper((ProcessorTarget) bean, "second:");
        });
        return processors;
    }

    @Test
    void appliesSamePipelineToConstructorMethodAndSupplierFactories() throws Exception {
        var factories = List.<BeanFactory>of(new ConstructorBeanFactory(ProcessorTarget.class),
                new MethodBeanFactory(ProcessorTarget.class.getMethod("fromFactory"), ProcessorTarget.class),
                new SupplierBeanFactory(ProcessorTarget::new));
        for (var factory : factories) {
            var calls = new AtomicInteger();
            try (var runtime = this.runtimeFor(BeanScope.SINGLETON, false, factory, List.of(this.wrapper(calls)))) {
                runtime.start();
                assertEquals("wrapped:configured", runtime.get(ProcessorTarget.class).message());
                assertEquals(1, calls.get());
            }
        }
    }

    @Test
    void lazySingletonProcessesOnlyOnFirstLookup() {
        var calls = new AtomicInteger();
        try (var runtime = this.runtimeFor(BeanScope.SINGLETON, true,
                new SupplierBeanFactory(ProcessorTarget::new), List.of(this.wrapper(calls)))) {
            runtime.start();
            assertEquals(0, calls.get());
            var first = runtime.get(ProcessorTarget.class);
            assertSame(first, runtime.get(ProcessorTarget.class));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void prototypeCreatesAndProcessesEveryInstanceWithoutContainerShutdown() {
        var originals = new ArrayList<ProcessorTarget>();
        var calls = new AtomicInteger();
        var factory = new SupplierBeanFactory(() -> {
            var target = new ProcessorTarget();
            originals.add(target);
            return target;
        });
        try (var runtime = this.runtimeFor(BeanScope.PROTOTYPE, false, factory, List.of(this.wrapper(calls)))) {
            runtime.start();
            var first = runtime.get(ProcessorTarget.class);
            var second = runtime.get(ProcessorTarget.class);
            assertNotSame(first, second);
            assertEquals("wrapped:configured", first.message());
            assertEquals("wrapped:configured", second.message());
            assertEquals(2, calls.get());
        }
        for (var original : originals) {
            assertEquals(List.of("init:configured:true"), original.getEvents());
        }
    }

    @Test
    void checkedProcessorFailureCleansOriginalAndIsCachedForLazySingleton() {
        var original = new ProcessorTarget();
        original.setFailClose(true);
        var cause = new IOException("processor failed");
        var calls = new AtomicInteger();
        BeanProcessor processor = (definition, bean) -> {
            if (definition.type().type() == ProcessorTarget.class) {
                calls.incrementAndGet();
                throw cause;
            }
            return bean;
        };
        try (var runtime = this.runtimeFor(BeanScope.SINGLETON, true,
                new SupplierBeanFactory(() -> original), List.of(processor))) {
            runtime.start();
            var failure = assertThrows(BeanCreationException.class, () -> runtime.get(ProcessorTarget.class));
            assertSame(cause, failure.getCause());
            assertTrue(failure.getMessage().contains("BeanProcessor 0"));
            assertTrue(failure.getMessage().contains(ProcessorTarget.class.getName()));
            assertEquals(1, failure.getSuppressed().length);
            assertSame(failure, assertThrows(BeanCreationException.class, () -> runtime.get(ProcessorTarget.class)));
            assertEquals(1, calls.get());
        }
        assertEquals(List.of("init:configured:true", "destroy", "close"), original.getEvents());
    }

    @Test
    void rejectsNullAndIncompatibleResultsBeforeNextProcessorAndCleansOriginal() {
        for (var invalid : new Object[]{null, new Object()}) {
            var original = new ProcessorTarget();
            var laterCalls = new AtomicInteger();
            BeanProcessor invalidProcessor = (definition, bean) -> definition.type().type() == ProcessorTarget.class ? invalid : bean;
            BeanProcessor later = (definition, bean) -> {
                if (definition.type().type() == ProcessorTarget.class) {
                    laterCalls.incrementAndGet();
                }
                return bean;
            };
            try (var runtime = this.runtimeFor(BeanScope.SINGLETON, true,
                    new SupplierBeanFactory(() -> original), List.of(invalidProcessor, later))) {
                runtime.start();
                var failure = assertThrows(BeanCreationException.class, () -> runtime.get(ProcessorTarget.class));
                assertTrue(failure.getMessage().contains("Invalid BeanProcessor 0 result"));
                assertEquals(0, laterCalls.get());
            }
            assertEquals(List.of("init:configured:true", "destroy", "close"), original.getEvents());
        }
    }

    @Test
    void failedPrototypeIsCleanedAndNextRequestCanSucceed() {
        var originals = new ArrayList<ProcessorTarget>();
        var calls = new AtomicInteger();
        var factory = new SupplierBeanFactory(() -> {
            var target = new ProcessorTarget();
            originals.add(target);
            return target;
        });
        BeanProcessor processor = (definition, bean) -> {
            if (definition.type().type() == ProcessorTarget.class && calls.getAndIncrement() == 0) {
                throw new IOException("first attempt");
            }
            return bean;
        };
        try (var runtime = this.runtimeFor(BeanScope.PROTOTYPE, false, factory, List.of(processor))) {
            runtime.start();
            assertThrows(BeanCreationException.class, () -> runtime.get(ProcessorTarget.class));
            assertEquals("configured", runtime.get(ProcessorTarget.class).message());
        }
        assertEquals(List.of("init:configured:true", "destroy", "close"), originals.getFirst().getEvents());
        assertEquals(List.of("init:configured:true"), originals.getLast().getEvents());
    }

    @Test
    void processorErrorKeepsOriginalFailureAndCleansOriginal() {
        var original = new ProcessorTarget();
        var cause = new AssertionError("processor error");
        BeanProcessor processor = (definition, bean) -> {
            if (definition.type().type() == ProcessorTarget.class) {
                throw cause;
            }
            return bean;
        };
        var runtime = this.runtimeFor(BeanScope.SINGLETON, false, new SupplierBeanFactory(() -> original), List.of(processor));
        assertSame(cause, assertThrows(AssertionError.class, runtime::start));
        runtime.close();
        assertEquals(List.of("init:configured:true", "destroy", "close"), original.getEvents());
    }

    @Test
    void laterProcessorFailureCleansOriginalWithoutClosingIntermediateWrapper() {
        var original = new ProcessorTarget();
        var wrapper = new ProcessorWrapper(original, "wrapped:");
        var cause = new IOException("second processor failed");
        BeanProcessor first = (definition, bean) -> definition.type().type() == ProcessorTarget.class ? wrapper : bean;
        BeanProcessor second = (definition, bean) -> {
            if (definition.type().type() == ProcessorTarget.class) {
                assertSame(wrapper, bean);
                throw cause;
            }
            return bean;
        };
        var runtime = this.runtimeFor(BeanScope.SINGLETON, false,
                new SupplierBeanFactory(() -> original), List.of(first, second));
        var failure = assertThrows(BeanCreationException.class, runtime::start);
        assertSame(cause, failure.getCause());
        runtime.close();
        assertEquals(List.of("init:configured:true", "destroy", "close"), original.getEvents());
        assertTrue(wrapper.getEvents().isEmpty());
    }

    @Test
    void sharedExposedObjectDoesNotMergeOwnershipOfDistinctOriginals() {
        var first = new ProcessorTarget();
        var second = new ProcessorTarget();
        var exposed = new ProcessorWrapper(first, "shared:");
        var registry = this.registryFor(BeanScope.SINGLETON, false, new SupplierBeanFactory(() -> first));
        var base = BeanDefinitions.of(ProcessorTarget.class, new SupplierBeanFactory(() -> second));
        registry.register(new BeanDefinition(new BeanKey(base.source().id(), "second"), base.type(), "second",
                base.scope(), base.lazy(), base.primary(), base.source(), base.factory()));
        BeanProcessor processor = (definition, bean) -> definition.type().type() == ProcessorTarget.class ? exposed : bean;
        try (var runtime = new WeftRuntime(registry, this.properties(), List.of(processor))) {
            runtime.start();
            assertSame(exposed, runtime.get(ProcessorTarget.class, "second"));
        }
        assertEquals(List.of("init:configured:true", "destroy", "close"), first.getEvents());
        assertEquals(List.of("init:configured:true", "destroy", "close"), second.getEvents());
        assertTrue(exposed.getEvents().isEmpty());
    }

    private BeanProcessor wrapper(AtomicInteger calls) {
        return (definition, bean) -> {
            if (definition.type().type() != ProcessorTarget.class) {
                return bean;
            }
            calls.incrementAndGet();
            return new ProcessorWrapper((ProcessorTarget) bean, "wrapped:");
        };
    }

    private BeanRegistry registryFor(BeanScope scope, boolean lazy, BeanFactory factory) {
        var registry = new BeanRegistry();
        registry.register(BeanDefinitions.of(ProcessorTarget.class, scope, lazy, factory));
        registry.register(BeanDefinitions.of(SimpleBean.class));
        return registry;
    }

    private WeftRuntime runtimeFor(BeanScope scope, boolean lazy, BeanFactory factory, List<BeanProcessor> processors) {
        return new WeftRuntime(this.registryFor(scope, lazy, factory), this.properties(), processors);
    }

    private PropertyResolver properties() {
        return new PropertyResolver(Map.of("app.text", "configured"), Map.of(), Map.of());
    }

}
