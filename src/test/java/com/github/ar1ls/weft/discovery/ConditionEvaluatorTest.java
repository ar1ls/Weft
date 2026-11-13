package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.discovery.fixtures.condition.ConditionSamples;
import com.github.ar1ls.weft.properties.PropertyResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    @Test
    void defaultConditionRequiresPresenceAndRejectsOnlyFalse() throws Exception {
        assertFalse(this.matches("enabled", Map.of()));
        for (var value : new String[]{"false", "FALSE", "False"}) {
            assertFalse(this.matches("enabled", Map.of("app.enabled", value)));
        }
        for (var value : new String[]{"", "true", "0", " false "}) {
            assertTrue(this.matches("enabled", Map.of("app.enabled", value)));
        }
    }

    @Test
    void requiresEveryKeyAndComparesExpectedValueExactly() throws Exception {
        assertTrue(this.matches("multiple", Map.of("app.mode", "dev", "app.region", "dev")));
        assertFalse(this.matches("multiple", Map.of("app.mode", "dev")));
        assertFalse(this.matches("multiple", Map.of("app.mode", "dev", "app.region", "DEV")));
        assertFalse(this.matches("multiple", Map.of("app.mode", "dev", "app.region", " dev ")));
    }

    @Test
    void matchIfMissingDoesNotOverridePresentMismatchOrEmpty() throws Exception {
        assertTrue(this.matches("optional", Map.of()));
        assertTrue(this.matches("optional", Map.of("optional", "yes")));
        assertFalse(this.matches("optional", Map.of("optional", "")));
        assertFalse(this.matches("optional", Map.of("optional", "no")));
    }

    @Test
    void combinesPropertyAndEnvironmentConditions() throws Exception {
        assertTrue(this.matches("combined", Map.of("enabled", "true", "APP_MODE", "dev")));
        assertFalse(this.matches("combined", Map.of("enabled", "false", "APP_MODE", "dev")));
        assertFalse(this.matches("combined", Map.of("enabled", "true")));
        assertTrue(this.matches("unconditional", Map.of()));
    }

    @Test
    void environmentConditionUsesExactKeyAndSharedSourcePrecedence() throws Exception {
        var method = ConditionSamples.class.getMethod("environment");
        var environment = Map.of("APP_MODE", "prod");
        assertTrue(ConditionEvaluator.matches(method,
                new PropertyResolver(Map.of("APP_MODE", "dev"), Map.of("APP_MODE", "prod"), environment)));
        assertTrue(ConditionEvaluator.matches(method,
                new PropertyResolver(Map.of(), Map.of("APP_MODE", "dev"), environment)));
        assertTrue(ConditionEvaluator.matches(method,
                new PropertyResolver(Map.of(), Map.of(), Map.of("APP_MODE", "dev"))));
        assertFalse(this.matches("environment", Map.of("app_mode", "dev")));
    }

    @Test
    void rejectsMalformedConditionsWithLocation() throws Exception {
        for (var name : new String[]{"emptyNames", "blankName"}) {
            var method = ConditionSamples.class.getMethod(name);
            var failure = assertThrows(DefinitionException.class,
                    () -> ConditionEvaluator.matches(method, new PropertyResolver(Map.of(), Map.of(), Map.of())));
            assertTrue(failure.getMessage().contains(name));
        }
    }

    @Test
    void leavesMissingBeanConditionForRegistrySelection() throws Exception {
        assertTrue(this.matches("fallback", Map.of()));
    }

    private boolean matches(String method, Map<String, String> properties) throws Exception {
        return ConditionEvaluator.matches(ConditionSamples.class.getMethod(method),
                new PropertyResolver(properties, Map.of(), Map.of()));
    }

}
