package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.PairDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.ProviderDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.TestBeanFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        assertEquals(List.of(simple.key(), dependent.key()), this.targets(plan.beans().get(pair.key())));
        assertEquals(List.of(simple.key()), this.targets(plan.beans().get(dependent.key())));
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

    @Test
    void bindsProviderTargetWithoutAddingDirectCreationEdge() {
        var consumer = BeanDefinitions.of(ProviderDependentBean.class);
        var target = BeanDefinitions.of(SimpleBean.class);
        var resolver = new DependencyResolver(List.of(consumer, target));

        var plan = new PlanBuilder().build(resolver);

        var argument = plan.beans().get(consumer.key()).arguments().getFirst();
        assertEquals(List.of(target.key()), argument.targets());
        assertEquals(DependencyKind.PROVIDER, argument.descriptor().kind());
        assertEquals(List.of(consumer.key(), target.key()), plan.creationOrder());
    }

    private List<BeanKey> targets(CreationPlan plan) {
        var targets = new ArrayList<BeanKey>();
        for (var argument : plan.arguments()) {
            targets.addAll(argument.targets());
        }
        return targets;
    }

}
