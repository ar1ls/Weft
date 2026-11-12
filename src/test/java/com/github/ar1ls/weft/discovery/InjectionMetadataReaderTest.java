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
import com.github.ar1ls.weft.runtime.fixtures.selection.Formatter;
import com.github.ar1ls.weft.runtime.fixtures.selection.WrapperMetadataFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void extractsOptionalListAndSetElementTypes() throws Exception {
        var method = WrapperMetadataFixtures.class.getMethod("valid", Optional.class, List.class, Set.class);
        var dependencies = InjectionMetadataReader.readParameters(method);

        assertEquals(DependencyKind.OPTIONAL, dependencies.get(0).kind());
        assertEquals(DependencyKind.LIST, dependencies.get(1).kind());
        assertEquals(DependencyKind.SET, dependencies.get(2).kind());
        for (var dependency : dependencies) {
            assertEquals(TypeRef.of(Formatter.class), dependency.type());
        }
    }

    @Test
    void rejectsMalformedCollectionAndOptionalWrappers() {
        for (var method : WrapperMetadataFixtures.class.getDeclaredMethods()) {
            if (List.of("valid", "unsupported", "blank").contains(method.getName())) {
                continue;
            }
            var exception = assertThrows(DefinitionException.class, () -> InjectionMetadataReader.readParameters(method));

            assertTrue(exception.getMessage().contains(method.getName()));
            assertTrue(exception.getMessage().contains("parameter 0"));
        }
    }

    @Test
    void rejectsBlankNamedQualifier() throws Exception {
        var method = WrapperMetadataFixtures.class.getMethod("blank", Formatter.class);

        assertThrows(DefinitionException.class, () -> InjectionMetadataReader.readParameters(method));
    }

}
