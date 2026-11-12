package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.InjectionPoint;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.BeanCreator;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SupplierBeanFactoryTest {

    @Test
    void invokesSupplierWithoutCachingResult() {
        var factory = new SupplierBeanFactory(SimpleBean::new);

        assertTrue(factory.dependencies().isEmpty());
        assertNotSame(factory.create(new Object[0]), factory.create(new Object[0]));
    }

    @Test
    void passesExplicitArgumentsAndCopiesDependencyMetadata() {
        var dependency = new DependencyDescriptor(TypeRef.of(SimpleBean.class), "", DependencyKind.DIRECT,
                new InjectionPoint("supplier dependency"));
        var dependencies = new ArrayList<>(List.of(dependency));
        var factory = new SupplierBeanFactory(dependencies,
                arguments -> new ConstructorDependentBean((SimpleBean) arguments[0]));
        dependencies.clear();
        var simple = new SimpleBean();

        var bean = (ConstructorDependentBean) factory.create(new Object[]{simple});

        assertEquals(List.of(dependency), factory.dependencies());
        assertSame(simple, bean.dependency());
    }

    @Test
    void rejectsArgumentsBeforeCallingSupplier() {
        var calls = new AtomicInteger();
        var factory = new SupplierBeanFactory(() -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });

        assertThrows(IllegalArgumentException.class, () -> factory.create(new Object[]{new Object()}));
        assertEquals(0, calls.get());
    }

    @Test
    void creatorRejectsNullAndPreservesSupplierFailure() {
        var creator = new BeanCreator();
        var nullDefinition = BeanDefinitions.of(SimpleBean.class, new SupplierBeanFactory(() -> null));
        var cause = new IllegalStateException("Supplier probe");
        var failing = BeanDefinitions.of(SimpleBean.class, new SupplierBeanFactory(() -> {
            throw cause;
        }));

        assertThrows(BeanCreationException.class, () -> creator.create(nullDefinition));
        var exception = assertThrows(BeanCreationException.class, () -> creator.create(failing));
        assertSame(cause, exception.getCause());
    }

    @Test
    void runtimeResolvesDeclaredSupplierDependencies() {
        var dependency = new DependencyDescriptor(TypeRef.of(SimpleBean.class), "", DependencyKind.DIRECT,
                new InjectionPoint("supplier dependency"));
        var factory = new SupplierBeanFactory(List.of(dependency),
                arguments -> new ConstructorDependentBean((SimpleBean) arguments[0]));
        var registry = new BeanRegistry();
        registry.register(BeanDefinitions.of(ConstructorDependentBean.class, factory));
        registry.register(BeanDefinitions.of(SimpleBean.class, new SupplierBeanFactory(SimpleBean::new)));
        var runtime = new WeftRuntime(registry);

        runtime.start();

        assertSame(runtime.get(SimpleBean.class), runtime.get(ConstructorDependentBean.class).dependency());
    }

}
