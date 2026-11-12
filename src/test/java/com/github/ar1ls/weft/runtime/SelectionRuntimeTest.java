package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.selection.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SelectionRuntimeTest {

    @Test
    void sharesNamedAndPrimarySelectionAcrossConstructorGetAndProvider() {
        var definitions = this.annotated(BracketFormatter.class, PlainFormatter.class);
        definitions.add(BeanDefinitions.of(ConstructorSelectionBean.class));
        try (var runtime = this.runtimeFor(definitions)) {
            runtime.start();
            var bean = runtime.get(ConstructorSelectionBean.class);

            assertSame(runtime.get(BracketFormatter.class), runtime.get(Formatter.class));
            assertSame(runtime.get(Formatter.class), bean.primary());
            assertSame(runtime.get(Formatter.class, "plain"), bean.named());
            assertSame(bean.named(), bean.provider().get());
            assertSame(bean.primary(), bean.optional().orElseThrow());
            assertEquals(List.of(bean.primary(), bean.named()), bean.list());
            assertEquals(Set.of(bean.named()), bean.namedSet());
            assertThrows(UnsupportedOperationException.class, () -> bean.list().clear());
            assertThrows(UnsupportedOperationException.class, () -> bean.namedSet().clear());
        }
    }

    @Test
    void fieldInjectionUsesSameSelectionAndStableSetOrder() {
        var definitions = this.annotated(PlainFormatter.class, BracketFormatter.class);
        definitions.add(BeanDefinitions.of(FieldSelectionBean.class));
        try (var runtime = this.runtimeFor(definitions)) {
            runtime.start();
            var bean = runtime.get(FieldSelectionBean.class);

            assertSame(runtime.get(Formatter.class), bean.getPrimary());
            assertSame(runtime.get(Formatter.class, "plain"), bean.getNamed());
            assertSame(bean.getNamed(), bean.getProvider().get());
            assertTrue(bean.getMissing().isEmpty());
            assertEquals(List.of(bean.getPrimary(), bean.getNamed()), bean.getList());
            assertEquals(bean.getList(), new ArrayList<>(bean.getSet()));
            assertThrows(UnsupportedOperationException.class, () -> bean.getSet().clear());
        }
    }

    @Test
    void configurationMethodsSelectNamedAndPrimaryAmongSameDeclaredReturnType() {
        try (var runtime = this.runtimeFor(this.annotated(SelectionConfiguration.class))) {
            runtime.start();
            var bean = runtime.get(MethodSelectedBean.class);

            assertSame(runtime.get(Formatter.class, "plain"), bean.named());
            assertSame(runtime.get(Formatter.class), bean.primary().get());
            assertTrue(bean.missing().isEmpty());
            assertEquals(List.of(runtime.get(Formatter.class), bean.named()), bean.list());
            assertEquals(bean.list(), new ArrayList<>(bean.set()));
            assertThrows(MissingBeanException.class, () -> runtime.get(BracketFormatter.class));
        }
    }

    @Test
    void permitsAbsentOptionalAndCollections() {
        var definitions = List.of(BeanDefinitions.of(OptionalSelectionBean.class), BeanDefinitions.of(CollectionSelectionBean.class));
        try (var runtime = this.runtimeFor(definitions)) {
            runtime.start();

            assertTrue(runtime.get(OptionalSelectionBean.class).formatter().isEmpty());
            assertEquals(List.of(), runtime.get(CollectionSelectionBean.class).list());
            assertEquals(Set.of(), runtime.get(CollectionSelectionBean.class).set());
        }
    }

    @Test
    void rejectsAmbiguousOptionalAndProviderBeforeAnyFactoryRuns() {
        for (var consumer : List.of(OptionalSelectionBean.class, ProviderSelectionBean.class)) {
            var calls = new AtomicInteger();
            var bracket = BeanDefinitions.of(BracketFormatter.class, new SupplierBeanFactory(() -> {
                calls.incrementAndGet();
                return new BracketFormatter();
            }));
            var plain = BeanDefinitions.of(PlainFormatter.class);

            var exception = assertThrows(AmbiguousBeanException.class,
                    () -> this.runtimeFor(List.of(BeanDefinitions.of(consumer), bracket, plain)));

            assertEquals(0, calls.get());
            assertTrue(exception.getMessage().contains(consumer.getName()));
            assertTrue(exception.getMessage().contains("parameter 0"));
        }
    }

    @Test
    void optionalDoesNotTurnCreationFailureIntoAbsence() {
        var cause = new IllegalStateException("formatter failed");
        var target = BeanDefinitions.of(BracketFormatter.class, BeanScope.SINGLETON, true, new SupplierBeanFactory(() -> {
            throw cause;
        }));
        try (var runtime = this.runtimeFor(List.of(BeanDefinitions.of(OptionalSelectionBean.class), target))) {
            var exception = assertThrows(BeanCreationException.class, runtime::start);

            assertSame(cause, exception.getCause());
        }
    }

    @Test
    void collectionOrderDoesNotDependOnRegistrationOrderOrPrimary() {
        for (var order : List.of(List.of(PlainFormatter.class, BracketFormatter.class), List.of(BracketFormatter.class, PlainFormatter.class))) {
            var definitions = this.annotated(order.toArray(Class<?>[]::new));
            definitions.add(BeanDefinitions.of(CollectionSelectionBean.class));
            try (var runtime = this.runtimeFor(definitions)) {
                runtime.start();
                var bean = runtime.get(CollectionSelectionBean.class);

                assertInstanceOf(BracketFormatter.class, bean.list().get(0));
                assertInstanceOf(PlainFormatter.class, bean.list().get(1));
                assertEquals(bean.list(), new ArrayList<>(bean.set()));
            }
        }
    }

    @Test
    void listKeepsDefinitionsWhileSetUsesNormalEqualitySemantics() {
        var instance = new BracketFormatter();
        var first = BeanDefinitions.of(BracketFormatter.class, new SupplierBeanFactory(() -> instance));
        var second = new BeanDefinition(new BeanKey(first.source().id(), "second"), first.type(), "", first.scope(),
                false, false, first.source(), first.factory());
        try (var runtime = this.runtimeFor(List.of(first, second, BeanDefinitions.of(CollectionSelectionBean.class)))) {
            runtime.start();
            var bean = runtime.get(CollectionSelectionBean.class);

            assertEquals(List.of(instance, instance), bean.list());
            assertEquals(Set.of(instance), bean.set());
        }
    }

    @Test
    void namedProviderRetainsPrototypeScope() {
        var definitions = this.annotated(BracketFormatter.class);
        var base = this.annotated(PlainFormatter.class).getFirst();
        definitions.add(new BeanDefinition(base.key(), base.type(), base.name(), BeanScope.PROTOTYPE, false, false,
                base.source(), base.factory()));
        definitions.add(BeanDefinitions.of(ConstructorSelectionBean.class));
        try (var runtime = this.runtimeFor(definitions)) {
            runtime.start();
            var provider = runtime.get(ConstructorSelectionBean.class).provider();

            assertNotSame(provider.get(), provider.get());
            assertInstanceOf(PlainFormatter.class, provider.get());
        }
    }

    @Test
    void optionalAndCollectionEdgesParticipateInCycleValidation() {
        for (var type : List.of(OptionalCycleBean.class, ListCycleBean.class)) {
            var exception = assertThrows(DefinitionException.class,
                    () -> this.runtimeFor(List.of(BeanDefinitions.of(type))));

            assertTrue(exception.getMessage().contains("Circular bean dependency"));
        }
    }

    private ArrayList<BeanDefinition> annotated(Class<?>... types) {
        var reader = new AnnotationDefinitionReader();
        var source = new BeanSource("test", this.getClass().getClassLoader());
        var definitions = new ArrayList<BeanDefinition>();
        for (var type : types) {
            definitions.addAll(reader.read(type, source));
        }
        return definitions;
    }

    private WeftRuntime runtimeFor(List<BeanDefinition> definitions) {
        var registry = new BeanRegistry();
        for (var definition : definitions) {
            registry.register(definition);
        }
        return new WeftRuntime(registry);
    }

}
