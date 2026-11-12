package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.discovery.InjectionMetadataReader;
import com.github.ar1ls.weft.plan.DependencyResolver;
import com.github.ar1ls.weft.plan.PlanBuilder;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.runtime.factory.SupplierBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.properties.*;
import com.github.ar1ls.weft.spi.BeanFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesRuntimeTest {

    @Test
    void bindsPrefixedFieldsWithExplicitDefaultsAndScalarConversion() {
        var properties = this.source(Map.of("greeting.text", "Welcome, ", "greeting.attempts", "8", "greeting.mode", "LOCAL"));
        try (var runtime = this.runtimeFor(properties, BeanDefinitions.of(GreetingSettings.class))) {
            runtime.start();
            var settings = runtime.get(GreetingSettings.class);

            assertEquals("Welcome, ", settings.getText());
            assertEquals(8, settings.getRetries());
            assertTrue(settings.isEnabled());
            assertEquals(Duration.ofSeconds(5), settings.getTimeout());
            assertEquals(BindingMode.LOCAL, settings.getMode());
            assertEquals(Path.of("."), settings.getDirectory());
            assertEquals("", settings.getEmpty());
        }
    }

    @Test
    void hooksAndConstructorConsumersObserveBoundProperties() {
        var properties = new PropertyResolver(Map.of("app.text", "explicit"), Map.of("APP_LIMIT", "9"), Map.of("APP_REQUIRED", "present"));
        try (var runtime = this.runtimeFor(properties, BeanDefinitions.of(ConfiguredConsumer.class), BeanDefinitions.of(LifecycleSettings.class))) {
            runtime.start();
            var settings = runtime.get(ConfiguredConsumer.class).settings();

            assertEquals("explicit:9", settings.getObserved());
            assertEquals("present", settings.getRequired());
            assertEquals("initial", settings.getRetained());
        }
    }

    @Test
    void bindsSupplierAndMethodResultsThroughSamePipeline() throws Exception {
        var method = LifecycleSettings.class.getMethod("fromFactory");
        var factories = List.<BeanFactory>of(new SupplierBeanFactory(LifecycleSettings::new), new MethodBeanFactory(method, LifecycleSettings.class));
        for (var factory : factories) {
            try (var runtime = this.runtimeFor(this.source(Map.of("APP_REQUIRED", "present")), BeanDefinitions.of(LifecycleSettings.class, factory))) {
                runtime.start();
                var settings = runtime.get(LifecycleSettings.class);

                assertEquals("default:4", settings.getObserved());
                assertEquals("present", settings.getRequired());
            }
        }
    }

    @Test
    void childPrefixAppliesToInheritedPrivateFieldsAndDiFieldsStayInjected() {
        var properties = this.source(Map.of("child.inherited", "child value", "child.count", "7"));
        try (var runtime = this.runtimeFor(properties, BeanDefinitions.of(ChildSettings.class), BeanDefinitions.of(SimpleBean.class))) {
            runtime.start();
            var settings = runtime.get(ChildSettings.class);

            assertEquals("child value", settings.getInherited());
            assertEquals(7, settings.getCount());
            assertSame(runtime.get(SimpleBean.class), settings.getDependency());
        }
    }

    @Test
    void explicitEmptyStringDoesNotUseDefaultOrLowerPriorityValue() {
        var properties = new PropertyResolver(Map.of("greeting.text", ""), Map.of("greeting.text", "system"), Map.of("greeting.text", "env"));
        try (var runtime = this.runtimeFor(properties, BeanDefinitions.of(GreetingSettings.class))) {
            runtime.start();

            assertEquals("", runtime.get(GreetingSettings.class).getText());
        }
    }

    @Test
    void validatesRequiredAndInvalidValuesBeforeAnyFactoryIncludingLazyAndPrototype() {
        for (var scope : BeanScope.values()) {
            var calls = new AtomicInteger();
            var unrelated = BeanDefinitions.of(SimpleBean.class, new SupplierBeanFactory(() -> {
                calls.incrementAndGet();
                return new SimpleBean();
            }));
            var target = BeanDefinitions.of(RequiredSettings.class, scope, true, new SupplierBeanFactory(() -> {
                calls.incrementAndGet();
                return new RequiredSettings();
            }));

            var exception = assertThrows(PropertyBindingException.class,
                    () -> this.runtimeFor(this.source(Map.of()), unrelated, target));

            assertTrue(exception.getMessage().contains("app.required"));
            assertEquals(0, calls.get());
        }
        var exception = assertThrows(PropertyBindingException.class,
                () -> this.runtimeFor(this.source(Map.of("greeting.attempts", "invalid-value")), BeanDefinitions.of(GreetingSettings.class)));

        assertTrue(exception.getMessage().contains("retries"));
        assertFalse(exception.getMessage().contains("invalid-value"));
    }

    @Test
    void requiredEnvDoesNotUseEmptyLegacyDefault() {
        assertThrows(PropertyBindingException.class,
                () -> this.runtimeFor(this.source(Map.of()), BeanDefinitions.of(LifecycleSettings.class)));
    }

    @Test
    void presentEmptyEnvSatisfiesRequiredStringAndOverwritesOptionalInitializer() {
        var properties = this.source(Map.of("APP_REQUIRED", "", "APP_OPTIONAL", ""));
        try (var runtime = this.runtimeFor(properties, BeanDefinitions.of(LifecycleSettings.class))) {
            runtime.start();
            var settings = runtime.get(LifecycleSettings.class);

            assertEquals("", settings.getRequired());
            assertEquals("", settings.getRetained());
        }
    }

    @Test
    void eachPrototypeReceivesPreparedValues() {
        var definition = BeanDefinitions.of(GreetingSettings.class, BeanScope.PROTOTYPE, false, new ConstructorBeanFactory(GreetingSettings.class));
        try (var runtime = this.runtimeFor(this.source(Map.of("greeting.attempts", "11")), definition)) {
            runtime.start();
            var first = runtime.get(GreetingSettings.class);
            var second = runtime.get(GreetingSettings.class);

            assertNotSame(first, second);
            assertEquals(11, first.getRetries());
            assertEquals(11, second.getRetries());
        }
    }

    @Test
    void rejectsConflictsMultipleDefaultsAndUnsupportedFieldsDuringPlanning() {
        for (var type : List.of(FinalPropertyBean.class, StaticPropertyBean.class, ConflictingPropertyBean.class,
                InjectedPropertyBean.class, MultipleDefaultsBean.class, UnsupportedPropertyBean.class)) {
            var calls = new AtomicInteger();
            var definition = BeanDefinitions.of(type, new SupplierBeanFactory(() -> {
                calls.incrementAndGet();
                return null;
            }));

            assertThrows(DefinitionException.class, () -> this.runtimeFor(this.source(Map.of()), definition));

            assertEquals(0, calls.get());
        }
    }

    @Test
    void propertyValuesAreAbsentFromPlanDiagnostics() {
        var secret = "private-text-value";
        var resolver = new DependencyResolver(List.of(BeanDefinitions.of(RequiredSettings.class)));
        var plan = new PlanBuilder().build(resolver, this.source(Map.of("app.required", secret)));

        assertFalse(plan.toString().contains(secret));
        assertTrue(plan.toString().contains("app.required"));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.beans().values().iterator().next().properties().fields().clear());
    }

    @Test
    void propertiesAnnotationAloneDoesNotRegisterBean() {
        var source = new BeanSource("test", GreetingSettings.class.getClassLoader());

        assertTrue(new AnnotationDefinitionReader().read(GreetingSettings.class, source).isEmpty());
    }

    @Test
    void parameterAnnotationsFailExplicitlyInsteadOfBecomingDiDependencies() {
        for (var method : PropertyParameterFixtures.class.getDeclaredMethods()) {
            var exception = assertThrows(DefinitionException.class, () -> InjectionMetadataReader.readParameters(method));

            assertTrue(exception.getMessage().contains("parameters are not supported"));
        }
    }

    private PropertyResolver source(Map<String, String> explicit) {
        return new PropertyResolver(explicit, Map.of(), Map.of());
    }

    private WeftRuntime runtimeFor(PropertyResolver properties, BeanDefinition... definitions) {
        var registry = new BeanRegistry();
        for (var definition : definitions) {
            registry.register(definition);
        }
        return new WeftRuntime(registry, properties);
    }

}
