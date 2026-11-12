package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.ProviderDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.ProviderMetadataFixtures;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.configuration.ProviderMethodConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InjectionMetadataReaderTest {

    @Test
    void extractsProviderElementTypeFromConstructorParameter() {
        var factory = new ConstructorBeanFactory(ProviderDependentBean.class);

        var dependency = factory.dependencies().getFirst();

        assertEquals(DependencyKind.PROVIDER, dependency.kind());
        assertEquals(TypeRef.of(SimpleBean.class), dependency.type());
    }

    @Test
    void extractsProviderAfterDirectConfigurationOwner() throws Exception {
        var method = ProviderMethodConfiguration.class.getMethod("consumer", Provider.class);
        var factory = new MethodBeanFactory(method, ProviderMethodConfiguration.class);

        assertEquals(2, factory.dependencies().size());
        assertEquals(DependencyKind.DIRECT, factory.dependencies().get(0).kind());
        assertEquals(DependencyKind.PROVIDER, factory.dependencies().get(1).kind());
        assertEquals(TypeRef.of(SimpleBean.class), factory.dependencies().get(1).type());
    }

    @Test
    void rejectsRawWildcardNestedAndVariableProviderTypes() throws Exception {
        for (var name : List.of("raw", "wildcard", "nested", "variable")) {
            var method = ProviderMetadataFixtures.class.getMethod(name, Provider.class);

            var exception = assertThrows(DefinitionException.class, () -> InjectionMetadataReader.readParameters(method));

            assertTrue(exception.getMessage().contains("parameter 0"));
            assertTrue(exception.getMessage().contains(name));
        }
    }

}
