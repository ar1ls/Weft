package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Lazy;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.ConditionalExclusions;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalDependencyPrunerTest {

    private static final BeanSource SOURCE = new BeanSource("forgesync", ConditionalDependencyPrunerTest.class.getClassLoader());

    @Bean
    @ConditionalOnEnv(name = "CFG_CLUSTER_INDEX")
    public static class ClusterManager {
        public ClusterManager() {}
    }

    public static class BorderManager {
        @Inject ClusterManager clusterManager;
        public BorderManager() {}
    }

    public static class WeatherTimeRedisListener {
        @Inject ClusterManager clusterManager;
        public WeatherTimeRedisListener() {}
    }

    public static class TransitiveListener {
        @Inject BorderManager borderManager;
        public TransitiveListener() {}
    }

    public static class StandaloneService {
        public StandaloneService() {}
    }

    public static class ConstructorConsumer {
        final ClusterManager cluster;
        public ConstructorConsumer(ClusterManager cluster) {
            this.cluster = cluster;
        }
    }

    public static class ProviderConsumer {
        @Inject Provider<ClusterManager> cluster;
        public ProviderConsumer() {}
    }

    public static class OptionalConsumer {
        @Inject Optional<ClusterManager> clusterManager;
        public OptionalConsumer() {}
    }

    public static class CollectionConsumer {
        @Inject List<ClusterManager> managers;
        public CollectionConsumer() {}
    }

    public static class NeverRegistered {
        public NeverRegistered() {}
    }

    public static class TrulyBroken {
        @Inject NeverRegistered absent;
        public TrulyBroken() {}
    }

    public interface ClusterContract {}

    @Bean
    @ConditionalOnEnv(name = "CFG_CLUSTER_INDEX")
    public static class ConditionalContract implements ClusterContract {
        public ConditionalContract() {}
    }

    public static class ActiveContract implements ClusterContract {
        public ActiveContract() {}
    }

    public static class AlternativeConsumer {
        @Inject ClusterContract cluster;
        public AlternativeConsumer() {}
    }

    public static class LazyConsumer {
        @Inject @Lazy ClusterContract cluster;
        public LazyConsumer() {}
    }

    @Bean
    @Named("disabled")
    @ConditionalOnEnv(name = "CFG_CLUSTER_INDEX")
    public static class NamedCluster {
        public NamedCluster() {}
    }

    public static class OtherNameConsumer {
        @Inject @Named("enabled") NamedCluster cluster;
        public OtherNameConsumer() {}
    }

    @Configuration
    public static class ClusterConfiguration {
        public ClusterConfiguration() {}

        @Bean
        @ConditionalOnEnv(name = "CFG_CLUSTER_INDEX")
        public ClusterManager conditionalCluster() {
            return new ClusterManager();
        }
    }

    @Test
    void skipsDirectAndTransitiveConsumersButStartsUnrelatedBeans() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, ClusterManager.class);
        var registry = registry(BorderManager.class, WeatherTimeRedisListener.class,
                TransitiveListener.class, ConstructorConsumer.class, ProviderConsumer.class, StandaloneService.class);
        try (var runtime = new WeftRuntime(registry, properties, List.of(), List.of(), exclusions.entries())) {
            runtime.start();
            assertEquals(1, runtime.report().beans().size());
            assertNotNull(runtime.get(StandaloneService.class));
            assertThrows(MissingBeanException.class, () -> runtime.get(BorderManager.class));
            assertThrows(MissingBeanException.class, () -> runtime.get(TransitiveListener.class));
        }
    }

    @Test
    void optionalAndCollectionConsumersRemainActive() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, ClusterManager.class);
        try (var runtime = new WeftRuntime(registry(OptionalConsumer.class, CollectionConsumer.class),
                properties, List.of(), List.of(), exclusions.entries())) {
            runtime.start();
            assertTrue(runtime.get(OptionalConsumer.class).clusterManager.isEmpty());
            assertTrue(runtime.get(CollectionConsumer.class).managers.isEmpty());
        }
    }

    @Test
    void unrelatedMissingInjectionStillFailsStrictly() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, ClusterManager.class);
        assertThrows(MissingBeanException.class, () -> new WeftRuntime(registry(TrulyBroken.class),
                properties, List.of(), List.of(), exclusions.entries()));
    }

    @Test
    void matchingActiveProviderPreventsSkipping() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, ConditionalContract.class);
        try (var runtime = new WeftRuntime(registry(AlternativeConsumer.class, ActiveContract.class),
                properties, List.of(), List.of(), exclusions.entries())) {
            runtime.start();
            assertInstanceOf(ActiveContract.class, runtime.get(AlternativeConsumer.class).cluster);
        }
    }

    @Test
    void lazyDependenciesAreAlsoRequired() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, ConditionalContract.class);
        try (var runtime = new WeftRuntime(registry(LazyConsumer.class),
                properties, List.of(), List.of(), exclusions.entries())) {
            runtime.start();
            assertTrue(runtime.report().beans().isEmpty());
        }
    }

    @Test
    void differentQualifierDoesNotMakeMissingBeanConditional() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, NamedCluster.class);
        assertThrows(MissingBeanException.class, () -> new WeftRuntime(registry(OtherNameConsumer.class),
                properties, List.of(), List.of(), exclusions.entries()));
    }

    @Test
    void methodConditionsAreTrackedAsWell() {
        var properties = new PropertyResolver(Map.of());
        var exclusions = exclusions(properties, ClusterConfiguration.class);
        assertEquals(1, exclusions.entries().size());
        assertEquals(ClusterManager.class, exclusions.entries().getFirst().type().type());
        try (var runtime = new WeftRuntime(registry(BorderManager.class),
                properties, List.of(), List.of(), exclusions.entries())) {
            runtime.start();
            assertTrue(runtime.report().beans().isEmpty());
        }
    }

    @Test
    void matchingEnvironmentConditionLeavesBeanAvailable() {
        var properties = new PropertyResolver(Map.of("CFG_CLUSTER_INDEX", "0"));
        assertTrue(exclusions(properties, ClusterManager.class).entries().isEmpty());
    }

    private static ConditionalExclusions exclusions(PropertyResolver properties, Class<?> type) {
        var exclusions = new ConditionalExclusions(properties);
        exclusions.inspect(type, SOURCE);
        return exclusions;
    }

    private static BeanRegistry registry(Class<?>... types) {
        var registry = new BeanRegistry();
        for (var type : types) {
            registry.register(Definitions.of(type, SOURCE.id()));
        }
        return registry;
    }
}
