package com.github.ar1ls.weft.properties;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PropertyResolverTest {

    @Test
    void usesExplicitThenSystemThenEnvironmentAndPreservesEmptyValues() {
        var resolver = new PropertyResolver(
                Map.of("all", "explicit", "empty", ""),
                Map.of("all", "system", "system", "system", "empty", "fallback"),
                Map.of("all", "environment", "system", "environment", "environment", "environment")
        );

        assertEquals(Optional.of("explicit"), resolver.find("all"));
        assertEquals(Optional.of("system"), resolver.find("system"));
        assertEquals(Optional.of("environment"), resolver.find("environment"));
        assertEquals(Optional.of(""), resolver.find("empty"));
        assertEquals(Optional.empty(), resolver.find("missing"));
    }

    @Test
    void snapshotsInputMapsAndUsesExactKeys() {
        var source = new HashMap<>(Map.of("app.value", "before"));
        var resolver = new PropertyResolver(source, Map.of(), Map.of("APP_VALUE", "env"));
        source.put("app.value", "after");

        assertEquals(Optional.of("before"), resolver.find("app.value"));
        assertEquals(Optional.of("env"), resolver.find("APP_VALUE"));
        assertTrue(resolver.find("app_value").isEmpty());
    }

}
