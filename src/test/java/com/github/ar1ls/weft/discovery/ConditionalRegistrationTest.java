package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.fixtures.condition.ConditionalConfiguration;
import com.github.ar1ls.weft.discovery.fixtures.condition.DisabledBean;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalRegistrationTest {

    @Test
    void disabledClassSkipsConstructorMetadataAndConfigurationSkipsAllFactories() {
        var reader = new AnnotationDefinitionReader(new PropertyResolver(Map.of(), Map.of(), Map.of()));
        var source = new BeanSource("conditions", DisabledBean.class.getClassLoader());
        assertTrue(reader.read(DisabledBean.class, source).isEmpty());
        assertTrue(reader.read(ConditionalConfiguration.class, source).isEmpty());
    }

    @Test
    void selectsFactoriesBeforeDependencyPlanningRegardlessOfInputOrder() {
        for (var mode : List.of("dev", "prod")) {
            for (var types : List.of(List.of(DisabledBean.class, ConditionalConfiguration.class),
                    List.of(ConditionalConfiguration.class, DisabledBean.class))) {
                var properties = new PropertyResolver(Map.of("app.enabled", "true", "app.mode", mode), Map.of(), Map.of());
                var registry = new BeanRegistry();
                var reader = new AnnotationDefinitionReader(properties);
                var source = new BeanSource("conditions", ConditionalConfiguration.class.getClassLoader());
                for (var type : types) {
                    for (var definition : reader.read(type, source)) {
                        registry.register(definition);
                    }
                }
                assertEquals(2, registry.definitions().size());
                try (var runtime = new WeftRuntime(registry, properties)) {
                    runtime.start();
                    assertEquals(mode.equals("dev") ? "development" : "production", runtime.get(String.class));
                }
            }
        }
    }

    @Test
    void activeFactoryStillValidatesItsDependencies() {
        var properties = new PropertyResolver(Map.of("app.enabled", "true", "unsupported.enabled", "true"), Map.of(), Map.of());
        var reader = new AnnotationDefinitionReader(properties);
        var source = new BeanSource("conditions", ConditionalConfiguration.class.getClassLoader());
        assertThrows(DefinitionException.class, () -> reader.read(ConditionalConfiguration.class, source));
    }

}
