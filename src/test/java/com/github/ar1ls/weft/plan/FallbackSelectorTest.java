package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.fallback.FallbackConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FallbackSelectorTest {

    @Test
    void configurationResultTypeDoesNotTurnAnOrdinaryFallbackIntoAGroup() {
        var configuration = this.definition("configuration-result", FallbackConfiguration.class, List.of(Object.class));
        var other = this.definition("other-result", String.class, List.of(Object.class));
        var failure = assertThrows(DefinitionException.class, () -> FallbackSelector.select(List.of(configuration, other)));
        assertTrue(failure.getMessage().contains("Conflicting fallback definitions"));
        assertTrue(failure.getMessage().contains(configuration.key().toString()));
        assertTrue(failure.getMessage().contains(other.key().toString()));
    }

    @Test
    void rejectsUnknownConditionalConfigurationOwner() {
        var product = this.definition("product", Runnable.class, List.of());
        var invalid = new BeanDefinition(product.key(), product.type(), product.name(), product.scope(), product.lazy(),
                product.primary(), product.source(), product.factory(), product.missingTypes(), new BeanKey(product.source().id(), "missing-owner"));
        assertThrows(DefinitionException.class, () -> FallbackSelector.select(List.of(invalid)));
    }

    @Test
    void selectsFallbackOnlyWhenNoOrdinaryAssignableTypeExists() {
        var fallback = this.definition("default", Thread.class, List.of(Runnable.class));
        var ordinary = this.definition("custom", Runnable.class, List.of());
        assertEquals(List.of(fallback), FallbackSelector.select(List.of(fallback)));
        for (var definitions : List.of(List.of(fallback, ordinary), List.of(ordinary, fallback))) {
            assertEquals(List.of(ordinary), FallbackSelector.select(definitions));
        }
        var unrelated = this.definition("unrelated", String.class, List.of());
        assertEquals(List.of(unrelated, fallback), FallbackSelector.select(List.of(fallback, unrelated)));
    }

    @Test
    void requiresAbsenceOfEveryDeclaredContract() {
        var fallback = this.definition("default", String.class, List.of(CharSequence.class, Comparable.class));
        var ordinary = this.definition("number", Integer.class, List.of());
        assertEquals(List.of(ordinary), FallbackSelector.select(List.of(fallback, ordinary)));
    }

    @Test
    void rejectsCompetingFallbacksDeterministicallyBeforeAnyFactoryRuns() {
        var first = this.definition("a", Thread.class, List.of(Runnable.class));
        var second = this.definition("b", Runnable.class, List.of(Runnable.class));
        String message = null;
        for (var definitions : List.of(List.of(first, second), List.of(second, first))) {
            var registry = new BeanRegistry();
            for (var definition : definitions) {
                registry.register(definition);
            }
            var failure = assertThrows(DefinitionException.class, registry::freeze);
            assertTrue(failure.getMessage().contains(first.key().toString()));
            assertTrue(failure.getMessage().contains(second.key().toString()));
            assertFalse(registry.isFrozen());
            assertEquals(2, registry.definitions().size());
            if (message != null) {
                assertEquals(message, failure.getMessage());
            }
            message = failure.getMessage();
        }
    }

    @Test
    void ordinaryCandidateSuppressesAllCompetingFallbacks() {
        var first = this.definition("a", Thread.class, List.of(Runnable.class));
        var second = this.definition("b", Runnable.class, List.of(Runnable.class));
        var ordinary = this.definition("custom", Runnable.class, List.of());
        assertEquals(List.of(ordinary), FallbackSelector.select(List.of(first, ordinary, second)));
    }

    @Test
    void freezeIsIdempotentAndPreviousSnapshotRetainsCandidates() {
        var fallback = this.definition("default", Thread.class, List.of(Runnable.class));
        var ordinary = this.definition("custom", Runnable.class, List.of());
        var registry = new BeanRegistry();
        registry.register(fallback);
        registry.register(ordinary);
        var candidates = registry.definitions();
        var active = registry.freeze();
        assertEquals(2, candidates.size());
        assertEquals(List.of(ordinary), new ArrayList<>(active.values()));
        assertEquals(active, registry.freeze());
        assertThrows(UnsupportedOperationException.class, active::clear);
        assertThrows(IllegalStateException.class, () -> registry.register(fallback));
    }

    @Test
    void copiesMetadataAndRejectsUnrelatedFallbackContract() {
        var contracts = new ArrayList<Class<?>>();
        contracts.add(Runnable.class);
        var definition = this.definition("default", Thread.class, contracts);
        contracts.clear();
        assertEquals(List.of(Runnable.class), definition.missingTypes());
        assertThrows(UnsupportedOperationException.class, () -> definition.missingTypes().clear());
        var invalid = this.definition("invalid", String.class, List.of(Runnable.class));
        assertThrows(DefinitionException.class, () -> FallbackSelector.select(List.of(invalid)));
    }

    private BeanDefinition definition(String id, Class<?> type, List<Class<?>> missingTypes) {
        var source = new BeanSource("test", FallbackSelectorTest.class.getClassLoader());
        var factory = new SupplierBeanFactory(() -> {
            throw new AssertionError("Selection must not instantiate beans");
        });
        return new BeanDefinition(new BeanKey(source.id(), id), TypeRef.of(type), id, BeanScope.PROTOTYPE,
                true, true, source, factory, missingTypes);
    }

}
