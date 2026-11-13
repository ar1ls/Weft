package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.WeftBuilder;
import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.discovery.AnnotationDefinitionReader;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.runtime.fixtures.contextual.*;
import com.github.ar1ls.weft.runtime.fixtures.regression.BaseConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.regression.ChildConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.regression.OwnerIdentity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SourceDefaultsRuntimeTest {

    @Test
    void factoryDiscoversDefinitionContributorAndScanSourcesWhileExplicitDefaultsWin() throws Exception {
        var calls = new ArrayList<String>();
        var cf = new BeanSource("cf", this.getClass().getClassLoader());
        var pe = new BeanSource("pe", this.getClass().getClassLoader());
        var reader = new AnnotationDefinitionReader();
        var root = Path.of(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        var scan = new ScanSource(root, cf, List.of("absent.package"));
        var empty = new ScanSource(root,
                new BeanSource("empty", this.getClass().getClassLoader()), List.of("absent.package"));
        var builder = Weft.builder().register(this.inSource(Definitions.of(SourceConsumer.class), cf))
                .register(this.inSource(this.override(false), cf))
                .contributor(() -> reader.read(SourceConfiguration.class, pe))
                .scan(scan).scan(scan).scan(empty).scan(empty)
                .sourceDefaults(SourceLabel.class, source -> {
                    calls.add(source.id());
                    return new Label("auto:" + source.id());
                })
                .sourceDefault("cf", SourceLabel.class, new Label("manual"));
        try (var runtime = builder.start()) {
            var consumer = runtime.get(SourceConsumer.class);
            assertEquals("manual", consumer.getLabel().value());
            assertSame(consumer.getLabel(), consumer.getConstructorLabel());
            assertEquals("override", consumer.getOverrideLabel().value());
            assertEquals("auto:pe", runtime.get(SourceConsumerView.class, "factory").getLabel().value());
            assertEquals("auto:empty", runtime.get(SourceLabel.class, "empty").value());
            assertEquals(4, consumer.getLabels().size());
            assertEquals(List.of("pe", "empty"), calls);
        }
    }

    @Test
    void factoryOptionsAreSnapshotsAndNoSourcesMeansNoFactoryCalls() {
        var calls = new AtomicInteger();
        var builder = Weft.builder().sourceDefaults(SourceLabel.class, source -> {
            calls.incrementAndGet();
            return new Label(source.id());
        });
        var options = builder.build();
        builder.sourceDefaults(Runnable.class, source -> () -> {});
        assertEquals(1, options.sourceDefaultFactories().size());
        assertThrows(UnsupportedOperationException.class, () -> options.sourceDefaultFactories().clear());
        try (var runtime = WeftBootstrap.start(options)) {
            assertTrue(runtime.report().beans().isEmpty());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void duplicateFactoriesAreRejectedBeforeCallbacksEvenWithAnExplicitOverride() {
        var calls = new AtomicInteger();
        var source = new BeanSource("cf", this.getClass().getClassLoader());
        assertThrows(DefinitionException.class, () -> Weft.builder()
                .register(this.inSource(this.override(false), source))
                .sourceDefault("cf", SourceLabel.class, new Label("manual"))
                .sourceDefaults(SourceLabel.class, context -> {
                    calls.incrementAndGet();
                    return new Label(context.id());
                })
                .sourceDefaults(SourceLabel.class, context -> new Label("duplicate")).start());
        assertEquals(0, calls.get());
    }

    @Test
    void factoryFailuresIncludeTheSourceAndPreserveTheirCause() {
        var seed = Definitions.instance("seed", String.class, "seed", false);
        var nullResult = assertThrows(DefinitionException.class, () -> Weft.builder().register(seed)
                .sourceDefaults(SourceLabel.class, source -> null).start());
        assertTrue(nullResult.getMessage().contains("programmatic"));
        assertTrue(nullResult.getMessage().contains(SourceLabel.class.getName()));
        assertInstanceOf(NullPointerException.class, nullResult.getCause());

        var cause = new IllegalStateException("factory failed");
        var failure = assertThrows(DefinitionException.class, () -> Weft.builder().register(seed)
                .sourceDefaults(SourceLabel.class, source -> {
                    throw cause;
                }).start());
        assertSame(cause, failure.getCause());
        assertTrue(failure.getMessage().contains("programmatic"));
    }

    @Test
    void sourcesSelectTheirDefaultsForConstructorsFieldsAndFactoryImplementations() {
        var builder = this.builder();
        var consumer = Definitions.of(SourceConsumer.class);
        var source = new BeanSource("cf", this.getClass().getClassLoader());
        builder.register(new BeanDefinition(new BeanKey("cf", "consumer"), consumer.type(), "cf",
                consumer.scope(), false, false, source, consumer.factory()));
        var crossSource = Definitions.of(CrossSourceConsumer.class);
        builder.register(new BeanDefinition(new BeanKey("cf", "cross-source"), crossSource.type(), "",
                crossSource.scope(), false, false, source, crossSource.factory()));
        for (var definition : new AnnotationDefinitionReader().read(SourceConfiguration.class,
                new BeanSource("pe", this.getClass().getClassLoader()))) {
            builder.register(definition);
        }
        try (var runtime = builder.start()) {
            var cf = runtime.get(SourceConsumer.class, "cf");
            var pe = assertInstanceOf(SourceConsumer.class, runtime.get(SourceConsumerView.class, "factory"));
            this.assertFields(cf, "CF");
            this.assertFields(pe, "PE");
            assertSame(cf.getLabel(), cf.getConstructorLabel());
            assertSame(pe.getLabel(), pe.getConstructorLabel());
            assertNotSame(cf.getLabel(), pe.getLabel());
            assertSame(cf.getLabel(), runtime.get(SourceLabel.class, "cf"));
            assertSame(pe.getLabel(), runtime.get(SourceLabel.class, "pe"));
            assertSame(pe, runtime.get(CrossSourceConsumer.class).factory());
            assertThrows(AmbiguousBeanException.class, () -> runtime.get(SourceLabel.class));
        }
    }

    @Test
    void unmanagedObjectsUseOnlyTheExplicitSourceAndKeepNamedOverrides() {
        try (var runtime = this.builder().start()) {
            var created = runtime.create(SourceConsumer.class, "cf");
            this.assertFields(created, "CF");
            assertSame(created.getLabel(), created.getConstructorLabel());

            var external = new SourceConsumer(null);
            runtime.inject(external, "pe");
            this.assertFields(external, "PE");
            assertNull(external.getConstructorLabel());
            assertThrows(AmbiguousBeanException.class, () -> runtime.create(SourceConsumer.class));
            var untouched = new SourceConsumer(null);
            assertThrows(AmbiguousBeanException.class, () -> runtime.inject(untouched));
            assertNull(untouched.getLabel());
            assertThrows(IllegalArgumentException.class, () -> runtime.create(SourceConsumer.class, " "));
            assertThrows(IllegalArgumentException.class, () -> runtime.inject(external, " "));
        }
    }

    @Test
    void explicitBeanKeyDefaultOverridesPrimaryWithoutChangingContextFreeLookup() {
        var preferred = Definitions.instance("preferred", SourceLabel.class, new Label("preferred"), false);
        var other = this.override(true);
        try (var runtime = Weft.builder().register(preferred).register(other)
                .sourceDefault("cf", TypeRef.of(SourceLabel.class), preferred.key()).start()) {
            var created = runtime.create(SourceConsumer.class, "cf");
            assertEquals("preferred", created.getLabel().value());
            assertEquals("override", created.getOverrideLabel().value());
            assertSame(runtime.get(SourceLabel.class), created.getOverrideLabel());
            assertEquals("override", runtime.create(SourceConsumer.class, "unconfigured").getLabel().value());
        }
    }

    @Test
    void invalidAndDuplicateBindingsFailBeforeAnyFactoryRuns() {
        var creations = new AtomicInteger();
        var target = Definitions.supplied("target", TypeRef.of(SourceLabel.class), BeanScope.SINGLETON, false, () -> {
            creations.incrementAndGet();
            return new Label("unused");
        });
        var missing = new BeanKey("absent", "target");
        assertThrows(DefinitionException.class, () -> Weft.builder().register(target)
                .sourceDefault("cf", TypeRef.of(SourceLabel.class), missing).start());
        assertThrows(DefinitionException.class, () -> Weft.builder().register(target)
                .sourceDefault("cf", TypeRef.of(Runnable.class), target.key()).start());
        assertThrows(DefinitionException.class, () -> Weft.builder().register(target)
                .sourceDefault("cf", TypeRef.of(SourceLabel.class), target.key())
                .sourceDefault("cf", TypeRef.of(SourceLabel.class), target.key()).start());
        assertEquals(0, creations.get());
    }

    @Test
    void sourceDefaultsDoNotRedirectAnExactConfigurationOwner() {
        var source = new BeanSource("owners", this.getClass().getClassLoader());
        var reader = new AnnotationDefinitionReader();
        var builder = Weft.builder();
        for (var type : new Class<?>[]{BaseConfiguration.class, ChildConfiguration.class}) {
            for (var definition : reader.read(type, source)) {
                builder.register(definition);
            }
        }
        builder.sourceDefault("owners", TypeRef.of(BaseConfiguration.class), new BeanKey("owners", ChildConfiguration.class.getName()));
        try (var runtime = builder.start()) {
            assertEquals(BaseConfiguration.class, runtime.get(OwnerIdentity.class, "base").owner().getClass());
            assertEquals(ChildConfiguration.class, runtime.get(OwnerIdentity.class, "child").owner().getClass());
        }
    }

    @Test
    void buildSnapshotsTheBindingsAndDirectInstancesRemainBorrowed() {
        var builder = this.builder();
        var options = builder.build();
        builder.sourceDefault("later", SourceLabel.class, new Label("later"));
        assertEquals(2, options.sourceDefaults().size());
        assertThrows(UnsupportedOperationException.class, () -> options.sourceDefaults().clear());
        var closes = new AtomicInteger();
        AutoCloseable resource = () -> closes.incrementAndGet();
        try (var runtime = Weft.builder().sourceDefault("cf", AutoCloseable.class, resource).start()) {
            assertSame(resource, runtime.get(AutoCloseable.class));
        }
        assertEquals(0, closes.get());
    }

    private WeftBuilder builder() {
        return Weft.builder().sourceDefault("cf", SourceLabel.class, new Label("CF"))
                .sourceDefault("pe", SourceLabel.class, new Label("PE")).register(this.override(false));
    }

    private BeanDefinition inSource(BeanDefinition definition, BeanSource source) {
        return new BeanDefinition(new BeanKey(source.id(), definition.key().localId()), definition.type(), definition.name(),
                definition.scope(), definition.lazy(), definition.primary(), source, definition.factory());
    }

    private BeanDefinition override(boolean primary) {
        var definition = Definitions.instance("override", SourceLabel.class, new Label("override"), false);
        return new BeanDefinition(definition.key(), definition.type(), "override", definition.scope(), false,
                primary, definition.source(), definition.factory());
    }

    private void assertFields(SourceConsumer consumer, String expected) {
        assertEquals(expected, consumer.getLabel().value());
        assertSame(consumer.getLabel(), consumer.getProvider().get());
        assertSame(consumer.getLabel(), consumer.getOptional().orElseThrow());
        assertEquals(expected, consumer.getLazy().value());
        assertEquals("override", consumer.getOverrideLabel().value());
        assertEquals(3, consumer.getLabels().size());
        assertEquals(3, consumer.getLabelSet().size());
    }

}
