package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.*;
import com.github.ar1ls.weft.spi.BeanFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FieldLifecycleRuntimeTest {

    @Test
    void injectsInheritedPrivateFieldsBeforeHooksAndPublishesOnlyOnce() {
        var runtime = this.runtimeFor(BeanDefinitions.of(LifecycleBean.class), BeanDefinitions.of(SimpleBean.class));
        runtime.start();

        var bean = runtime.get(LifecycleBean.class);

        assertSame(bean, runtime.get(LifecycleBean.class));
        assertSame(runtime.get(SimpleBean.class), bean.getSimple());
        assertSame(bean.getSimple(), bean.getObserved());
        assertSame(bean.getSimple(), bean.getProvider().get());
        assertNotSame(bean.getSimple(), bean.getUntouched());
        assertEquals(List.of("constructor", "base", "child"), bean.getEvents());
    }

    @Test
    void initializesEachPrototypeAndSupportsProviderFields() {
        var runtime = this.runtimeFor(
                BeanDefinitions.of(LifecycleBean.class, BeanScope.PROTOTYPE, false, new ConstructorBeanFactory(LifecycleBean.class)),
                BeanDefinitions.of(SimpleBean.class, BeanScope.PROTOTYPE, false, new ConstructorBeanFactory(SimpleBean.class))
        );
        runtime.start();

        var first = runtime.get(LifecycleBean.class);
        var second = runtime.get(LifecycleBean.class);

        assertNotSame(first, second);
        assertNotSame(first.getSimple(), second.getSimple());
        assertNotSame(first.getProvider().get(), first.getProvider().get());
        assertEquals(List.of("constructor", "base", "child"), first.getEvents());
        assertEquals(first.getEvents(), second.getEvents());
    }

    @Test
    void providerFieldDoesNotCreateLazyTargetUntilRequested() {
        var calls = new AtomicInteger();
        var runtime = this.runtimeFor(
                BeanDefinitions.of(ProviderFieldBean.class),
                BeanDefinitions.of(SimpleBean.class, BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> {
                    calls.incrementAndGet();
                    return new SimpleBean();
                }))
        );
        runtime.start();

        var provider = runtime.get(ProviderFieldBean.class).getProvider();

        assertEquals(0, calls.get());
        assertSame(provider.get(), provider.get());
        assertEquals(1, calls.get());
    }

    @Test
    void providerFieldRequiresCandidateDuringPlanning() {
        var exception = assertThrows(MissingBeanException.class,
                () -> this.runtimeFor(BeanDefinitions.of(ProviderFieldBean.class)));

        assertTrue(exception.getMessage().contains("provider"));
        assertTrue(exception.getMessage().contains(SimpleBean.class.getName()));
    }

    @Test
    void appliesSamePipelineToMethodAndSupplierResults() throws Exception {
        var method = LifecycleBean.class.getMethod("fromFactory");
        var factories = List.<BeanFactory>of(new MethodBeanFactory(method, LifecycleBean.class), new SupplierBeanFactory(LifecycleBean::new));
        for (var factory : factories) {
            var runtime = this.runtimeFor(BeanDefinitions.of(LifecycleBean.class, factory), BeanDefinitions.of(SimpleBean.class));
            runtime.start();

            var bean = runtime.get(LifecycleBean.class);

            assertSame(runtime.get(SimpleBean.class), bean.getObserved());
            assertEquals(List.of("constructor", "base", "child"), bean.getEvents());
        }
    }

    @Test
    void rejectsMissingFieldDependencyBeforeAnyFactoryRuns() {
        var calls = new AtomicInteger();
        var definition = BeanDefinitions.of(LifecycleBean.class, new SupplierBeanFactory(() -> {
            calls.incrementAndGet();
            return new LifecycleBean();
        }));

        var exception = assertThrows(MissingBeanException.class, () -> this.runtimeFor(definition));

        assertEquals(0, calls.get());
        assertTrue(exception.getMessage().contains("simple"));
        assertTrue(exception.getMessage().contains(SimpleBean.class.getName()));
    }

    @Test
    void rejectsDirectFieldCycleBeforeCreation() {
        var calls = new AtomicInteger();
        var definition = BeanDefinitions.of(FieldCycleBean.class, new SupplierBeanFactory(() -> {
            calls.incrementAndGet();
            return new FieldCycleBean();
        }));

        assertThrows(DefinitionException.class, () -> this.runtimeFor(definition));

        assertEquals(0, calls.get());
    }

    @Test
    void rejectsInvalidFieldsAndHooksEvenForLazyBeans() {
        for (var type : List.of(StaticFieldBean.class, FinalFieldBean.class, InvalidHookBean.class, MultipleHooksBean.class)) {
            var calls = new AtomicInteger();
            var definition = BeanDefinitions.of(type, BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> {
                calls.incrementAndGet();
                return null;
            }));

            var exception = assertThrows(DefinitionException.class, () -> this.runtimeFor(definition));

            assertTrue(exception.getMessage().contains(type.getName()));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void unannotatedOverrideSuppressesInheritedHook() {
        var runtime = this.runtimeFor(BeanDefinitions.of(SuppressedHookBean.class));
        runtime.start();

        assertEquals(List.of(), runtime.get(SuppressedHookBean.class).getEvents());
    }

    @Test
    void annotatedOverrideRunsExactlyOnce() {
        var runtime = this.runtimeFor(BeanDefinitions.of(AnnotatedOverrideHookBean.class));
        runtime.start();

        assertEquals(List.of("override"), runtime.get(AnnotatedOverrideHookBean.class).getEvents());
    }

    @Test
    void cachesFailedLazySingletonWithoutPublishingIt() {
        var cause = new IllegalStateException("hook failure");
        var bean = new FailingHookBean(cause);
        var runtime = this.runtimeFor(BeanDefinitions.of(FailingHookBean.class, BeanScope.SINGLETON, true,
                new SupplierBeanFactory(() -> bean)));
        runtime.start();

        var first = assertThrows(BeanCreationException.class, () -> runtime.get(FailingHookBean.class));
        var second = assertThrows(BeanCreationException.class, () -> runtime.get(FailingHookBean.class));

        assertSame(first, second);
        assertSame(cause, first.getCause());
        assertEquals(1, bean.getAttempts());
    }

    @Test
    void hookFailureMakesStartupFail() {
        var cause = new IllegalArgumentException("startup hook failure");
        var runtime = this.runtimeFor(BeanDefinitions.of(FailingHookBean.class,
                new SupplierBeanFactory(() -> new FailingHookBean(cause))));

        var exception = assertThrows(BeanCreationException.class, runtime::start);

        assertSame(cause, exception.getCause());
        assertThrows(IllegalStateException.class, () -> runtime.get(FailingHookBean.class));
    }

    @Test
    void prototypeRetriesAfterHookFailure() {
        var cause = new IllegalStateException("prototype hook failure");
        var calls = new AtomicInteger();
        var runtime = this.runtimeFor(BeanDefinitions.of(FailingHookBean.class, BeanScope.PROTOTYPE, false,
                new SupplierBeanFactory(() -> {
                    calls.incrementAndGet();
                    return new FailingHookBean(cause);
                })));
        runtime.start();

        var first = assertThrows(BeanCreationException.class, () -> runtime.get(FailingHookBean.class));
        var second = assertThrows(BeanCreationException.class, () -> runtime.get(FailingHookBean.class));

        assertNotSame(first, second);
        assertSame(cause, first.getCause());
        assertSame(cause, second.getCause());
        assertEquals(2, calls.get());
    }

    @Test
    void providerCannotReturnCurrentBeanDuringPostConstruct() {
        var runtime = this.runtimeFor(BeanDefinitions.of(HookProviderCycleBean.class));

        var exception = assertThrows(BeanCreationException.class, runtime::start);

        assertInstanceOf(BeanCreationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Circular singleton creation"));
    }

    private WeftRuntime runtimeFor(BeanDefinition... definitions) {
        var registry = new BeanRegistry();
        for (var definition : definitions) {
            registry.register(definition);
        }
        return new WeftRuntime(registry);
    }

}
