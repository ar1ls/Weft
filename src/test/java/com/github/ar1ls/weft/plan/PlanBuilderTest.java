package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.PairDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.TestBeanFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlanBuilderTest {

    @Test
    void bindsArgumentsInParameterOrderAndOrdersDependenciesBeforeConsumers() {
        var pair = BeanDefinitions.of(PairDependentBean.class);
        var dependent = BeanDefinitions.of(ConstructorDependentBean.class);
        var simple = BeanDefinitions.of(SimpleBean.class);
        var resolver = new DependencyResolver(List.of(pair, dependent, simple));

        var plan = new PlanBuilder().build(resolver);

        assertEquals(List.of(simple.key(), dependent.key(), pair.key()), plan.creationOrder());
        assertEquals(List.of(simple.key(), dependent.key()), plan.beans().get(pair.key()).arguments());
        assertEquals(List.of(simple.key()), plan.beans().get(dependent.key()).arguments());
    }

    @Test
    void buildsPlanWithoutCallingFactories() {
        var calls = new AtomicInteger();
        var factory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var resolver = new DependencyResolver(List.of(
                BeanDefinitions.of(ConstructorDependentBean.class), BeanDefinitions.of(SimpleBean.class, factory)));

        new PlanBuilder().build(resolver);

        assertEquals(0, calls.get());
    }

    @Test
    void exposesImmutablePlan() {
        var definition = BeanDefinitions.of(SimpleBean.class);
        var resolver = new DependencyResolver(List.of(definition));
        var plan = new PlanBuilder().build(resolver);

        assertThrows(UnsupportedOperationException.class, () -> plan.beans().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.creationOrder().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.beans().get(definition.key()).arguments().clear());
    }

}
