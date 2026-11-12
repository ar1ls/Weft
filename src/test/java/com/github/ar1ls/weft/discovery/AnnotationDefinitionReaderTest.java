package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.discovery.fixtures.AnnotatedBean;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.PairDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.configuration.CyclicConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.FactoryConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.InheritedConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.MetadataConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.NonPublicFactoryConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.OverloadedConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.OverriddenConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.StaticOnlyConfiguration;
import com.github.ar1ls.weft.runtime.fixtures.configuration.VoidFactoryConfiguration;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class AnnotationDefinitionReaderTest {

    AnnotationDefinitionReader reader = new AnnotationDefinitionReader();
    BeanSource source = new BeanSource("test", FactoryConfiguration.class.getClassLoader());

    @Test
    void ignoresUnannotatedClass() {
        assertTrue(this.reader.read(SimpleBean.class, this.source).isEmpty());
    }

    @Test
    void readsOrdinaryBeanWithDefaultSettings() {
        var definitions = this.reader.read(AnnotatedBean.class, this.source);

        assertEquals(1, definitions.size());
        var definition = definitions.getFirst();
        assertEquals(TypeRef.of(AnnotatedBean.class), definition.type());
        assertEquals(BeanScope.SINGLETON, definition.scope());
        assertFalse(definition.lazy());
        assertFalse(definition.primary());
        assertEquals("", definition.name());
    }

    @Test
    void readsConfigurationOwnerAndFactoriesWithStableOrder() {
        var definitions = this.reader.read(FactoryConfiguration.class, this.source);

        assertEquals(4, definitions.size());
        assertEquals(TypeRef.of(FactoryConfiguration.class), definitions.getFirst().type());
        var repeated = this.reader.read(FactoryConfiguration.class, this.source);
        for (var index = 0; index < definitions.size(); index++) {
            assertEquals(definitions.get(index).key(), repeated.get(index).key());
        }
        assertThrows(UnsupportedOperationException.class, definitions::clear);
    }

    @Test
    void injectsMethodParametersAndUsesManagedConfigurationOwner() {
        var runtime = this.runtimeFor(FactoryConfiguration.class, BeanDefinitions.of(SimpleBean.class));
        runtime.start();

        var simple = runtime.get(SimpleBean.class);
        var owner = runtime.get(FactoryConfiguration.class);
        var dependent = runtime.get(ConstructorDependentBean.class);
        var pair = runtime.get(PairDependentBean.class);

        assertSame(simple, owner.getDependency());
        assertSame(dependent, owner.getLastDependent());
        assertSame(simple, dependent.dependency());
        assertSame(simple, pair.first());
        assertSame(dependent, pair.second());
        assertSame(dependent, runtime.get(ConstructorDependentBean.class));
    }

    @Test
    void staticFactoryDoesNotInstantiateLazyConfiguration() {
        var runtime = this.runtimeFor(StaticOnlyConfiguration.class);

        runtime.start();

        assertNotNull(runtime.get(SimpleBean.class));
    }

    @Test
    void inheritedMethodDependsOnActualConfigurationType() {
        var definitions = this.reader.read(InheritedConfiguration.class, this.source);
        var product = this.definitionFor(definitions, SimpleBean.class);

        assertEquals(TypeRef.of(InheritedConfiguration.class), product.factory().dependencies().getFirst().type());
        var runtime = this.runtimeFor(InheritedConfiguration.class);
        runtime.start();
        assertNotNull(runtime.get(SimpleBean.class));
    }

    @Test
    void overrideWithoutBeanAnnotationSuppressesInheritedFactory() {
        var definitions = this.reader.read(OverriddenConfiguration.class, this.source);

        assertEquals(1, definitions.size());
        assertEquals(TypeRef.of(OverriddenConfiguration.class), definitions.getFirst().type());
    }

    @Test
    void preservesDistinctKeysForOverloadedMethods() {
        var definitions = this.reader.read(OverloadedConfiguration.class, this.source);

        assertEquals(3, definitions.size());
        assertNotEquals(definitions.get(1).key(), definitions.get(2).key());
        assertTrue(definitions.get(1).key().localId().contains("simple("));
        assertTrue(definitions.get(2).key().localId().contains("simple("));
        assertThrows(DefinitionException.class, () -> this.runtimeFor(OverloadedConfiguration.class));
    }

    @Test
    void readsScopeLazyNamedAndPrimaryIndependentlyForOwnerAndProduct() {
        var definitions = this.reader.read(MetadataConfiguration.class, this.source);
        var owner = this.definitionFor(definitions, MetadataConfiguration.class);
        var product = this.definitionFor(definitions, SimpleBean.class);

        assertEquals(BeanScope.PROTOTYPE, owner.scope());
        assertTrue(owner.lazy());
        assertEquals("configuration", owner.name());
        assertFalse(owner.primary());
        assertEquals(BeanScope.PROTOTYPE, product.scope());
        assertTrue(product.lazy());
        assertEquals("product", product.name());
        assertTrue(product.primary());
    }

    @Test
    void rejectsNonPublicAndVoidFactoryMethods() {
        assertThrows(DefinitionException.class, () -> this.reader.read(NonPublicFactoryConfiguration.class, this.source));
        assertThrows(DefinitionException.class, () -> this.reader.read(VoidFactoryConfiguration.class, this.source));
    }

    @Test
    void detectsConfigurationOwnerCycleBeforeCreation() {
        var exception = assertThrows(DefinitionException.class,
                () -> this.runtimeFor(CyclicConfiguration.class, BeanDefinitions.of(SimpleBean.class)));

        assertTrue(exception.getMessage().contains("Circular bean dependency"));
        assertTrue(exception.getMessage().contains(CyclicConfiguration.class.getName()));
        assertTrue(exception.getMessage().contains(ConstructorDependentBean.class.getName()));
    }

    private BeanDefinition definitionFor(List<BeanDefinition> definitions, Class<?> type) {
        for (var definition : definitions) {
            if (definition.type().equals(TypeRef.of(type))) {
                return definition;
            }
        }
        throw new AssertionError("Missing definition: " + type.getName());
    }

    private WeftRuntime runtimeFor(Class<?> configuration, BeanDefinition... additional) {
        var registry = new BeanRegistry();
        for (var definition : this.reader.read(configuration, this.source)) {
            registry.register(definition);
        }
        for (var definition : additional) {
            registry.register(definition);
        }
        return new WeftRuntime(registry);
    }

}
