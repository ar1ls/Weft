package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.fixtures.condition.ConditionalConfiguration;
import com.github.ar1ls.weft.discovery.fixtures.condition.DisabledBean;
import com.github.ar1ls.weft.discovery.fixtures.condition.FallbackTask;
import com.github.ar1ls.weft.discovery.fixtures.condition.UnsupportedFallbackConfiguration;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
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
        for (var mode : List.of("dev", "prod", "unknown")) {
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
                assertEquals(mode.equals("unknown") ? 2 : 3, registry.definitions().size());
                try (var runtime = new WeftRuntime(registry, properties)) {
                    assertEquals(2, registry.definitions().size());
                    runtime.start();
                    var expected = switch (mode) {
                        case "dev" -> "development";
                        case "prod" -> "production";
                        default -> "fallback";
                    };
                    assertEquals(expected, runtime.get(String.class));
                }
            }
        }
    }

    @Test
    void ordinaryContributorSuppressesClassFallbackBeforeDependencyPlanning() {
        var properties = new PropertyResolver(Map.of(), Map.of(), Map.of());
        var reader = new AnnotationDefinitionReader(properties);
        var source = new BeanSource("conditions", FallbackTask.class.getClassLoader());
        var fallback = reader.read(FallbackTask.class, source).getFirst();
        assertEquals(List.of(Runnable.class), fallback.missingTypes());
        Runnable task = () -> {};
        var ordinary = BeanDefinitions.of(Runnable.class, new SupplierBeanFactory(() -> task));
        for (var definitions : List.of(List.of(fallback, ordinary), List.of(ordinary, fallback))) {
            var registry = new BeanRegistry();
            for (var definition : definitions) {
                registry.register(definition);
            }
            try (var runtime = new WeftRuntime(registry, properties)) {
                runtime.start();
                assertSame(task, runtime.get(Runnable.class));
                assertFalse(registry.definitions().containsKey(fallback.key()));
            }
        }
    }

    @Test
    void rejectsFallbackOnWholeConfiguration() {
        var reader = new AnnotationDefinitionReader(new PropertyResolver(Map.of(), Map.of(), Map.of()));
        var source = new BeanSource("conditions", UnsupportedFallbackConfiguration.class.getClassLoader());
        var failure = assertThrows(DefinitionException.class,
                () -> reader.read(UnsupportedFallbackConfiguration.class, source));
        assertTrue(failure.getMessage().contains("use @Bean methods"));
    }

    @Test
    void activeFactoryStillValidatesItsDependencies() {
        var properties = new PropertyResolver(Map.of("app.enabled", "true", "unsupported.enabled", "true"), Map.of(), Map.of());
        var reader = new AnnotationDefinitionReader(properties);
        var source = new BeanSource("conditions", ConditionalConfiguration.class.getClassLoader());
        assertThrows(DefinitionException.class, () -> reader.read(ConditionalConfiguration.class, source));
    }

}
