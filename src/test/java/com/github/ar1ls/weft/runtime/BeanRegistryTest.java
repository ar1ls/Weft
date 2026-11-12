package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeanRegistryTest {

    @Test
    void rejectsDuplicateKeys() {
        var registry = new BeanRegistry();
        var definition = BeanDefinitions.of(SimpleBean.class);
        registry.register(definition);

        var exception = assertThrows(DefinitionException.class, () -> registry.register(definition));

        assertTrue(exception.getMessage().contains(definition.key().toString()));
        assertSame(definition, registry.definitions().get(definition.key()));
    }

    @Test
    void rejectsRegistrationAfterFreeze() {
        var registry = new BeanRegistry();
        registry.freeze();

        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(BeanDefinitions.of(SimpleBean.class)));
    }

    @Test
    void returnsImmutableSnapshotIndependentOfLaterRegistrations() {
        var registry = new BeanRegistry();
        var snapshot = registry.definitions();
        var definition = BeanDefinitions.of(SimpleBean.class);

        registry.register(definition);

        assertTrue(snapshot.isEmpty());
        assertEquals(1, registry.definitions().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(definition.key(), definition));
    }

}
