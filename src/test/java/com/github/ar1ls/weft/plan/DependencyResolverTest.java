package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.SourceDefault;
import com.github.ar1ls.weft.api.exception.AmbiguousBeanException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.api.exception.MissingBeanException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.selection.BracketFormatter;
import com.github.ar1ls.weft.runtime.fixtures.selection.Formatter;
import com.github.ar1ls.weft.runtime.fixtures.selection.PlainFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyResolverTest {

    @Test
    void selectsSingleAssignableCandidate() {
        var bracket = this.definition(BracketFormatter.class, "bracket", "", false);
        var resolver = new DependencyResolver(List.of(bracket));

        assertEquals(bracket, resolver.resolve(Formatter.class));
        assertEquals(bracket, resolver.resolve(BracketFormatter.class));
        assertThrows(MissingBeanException.class, () -> resolver.resolve(PlainFormatter.class));
    }

    @Test
    void filtersByNameBeforeChoosingPrimary() {
        var bracket = this.definition(BracketFormatter.class, "bracket", "brackets", true);
        var plain = this.definition(PlainFormatter.class, "plain", "plain", false);
        var resolver = new DependencyResolver(List.of(plain, bracket));

        assertEquals(bracket, resolver.resolve(Formatter.class));
        assertEquals(plain, resolver.resolve(Formatter.class, "plain"));
        assertThrows(MissingBeanException.class, () -> resolver.resolve(Formatter.class, "missing"));
    }

    @Test
    void rejectsAmbiguityAndMultiplePrimariesWithoutSelectingByOrder() {
        for (var primary : List.of(false, true)) {
            var bracket = this.definition(BracketFormatter.class, "bracket", "same", primary);
            var plain = this.definition(PlainFormatter.class, "plain", "same", primary);
            var forward = new DependencyResolver(List.of(bracket, plain));
            var reverse = new DependencyResolver(List.of(plain, bracket));

            var first = assertThrows(AmbiguousBeanException.class, () -> forward.resolve(Formatter.class, "same"));
            var second = assertThrows(AmbiguousBeanException.class, () -> reverse.resolve(Formatter.class, "same"));

            assertEquals(first.getMessage(), second.getMessage());
            assertTrue(first.getMessage().contains(bracket.key().toString()));
            assertTrue(first.getMessage().contains(plain.key().toString()));
        }
    }

    @Test
    void permitsSeveralDefinitionsOfSameClassWhenNameSelectsOne() {
        var first = this.definition(BracketFormatter.class, "a", "first", false);
        var second = this.definition(BracketFormatter.class, "b", "second", false);
        var resolver = new DependencyResolver(List.of(first, second));

        assertEquals(first, resolver.resolve(BracketFormatter.class, "first"));
        assertEquals(second, resolver.resolve(BracketFormatter.class, "second"));
        assertThrows(AmbiguousBeanException.class, () -> resolver.resolve(BracketFormatter.class));
    }

    @Test
    void collectionIncludesAllCandidatesInStableKeyOrderRegardlessOfPrimary() {
        var bracket = this.definition(BracketFormatter.class, "z", "brackets", true);
        var plain = this.definition(PlainFormatter.class, "a", "plain", false);
        for (var order : List.of(List.of(bracket, plain), List.of(plain, bracket))) {
            var resolver = new DependencyResolver(order);

            for (var kind : List.of(DependencyKind.LIST, DependencyKind.SET)) {
                assertEquals(List.of(plain, bracket), resolver.resolve(this.request(kind, "")));
                assertEquals(List.of(bracket), resolver.resolve(this.request(kind, "brackets")));
                assertEquals(List.of(), resolver.resolve(this.request(kind, "absent")));
            }
        }
    }

    @Test
    void optionalAllowsAbsenceButDoesNotHideAmbiguity() {
        assertEquals(List.of(), new DependencyResolver(List.of()).resolve(this.request(DependencyKind.OPTIONAL, "")));
        var first = this.definition(BracketFormatter.class, "a", "", false);
        var second = this.definition(PlainFormatter.class, "b", "", false);
        var resolver = new DependencyResolver(List.of(first, second));

        assertThrows(AmbiguousBeanException.class, () -> resolver.resolve(this.request(DependencyKind.OPTIONAL, "")));
        assertThrows(AmbiguousBeanException.class, () -> resolver.resolve(this.request(DependencyKind.PROVIDER, "")));
        assertEquals(List.of(), resolver.resolve(this.request(DependencyKind.OPTIONAL, "absent")));
    }

    @Test
    void usesDeclaredFactoryTypeAndDoesNotPreferExactTypeOverPrimary() {
        var concrete = this.definition(BracketFormatter.class, "concrete", "", true);
        var base = this.definition(PlainFormatter.class, "contract", "", false);
        var contract = new BeanDefinition(base.key(), TypeRef.of(Formatter.class), "", base.scope(), false, false,
                base.source(), base.factory());
        var resolver = new DependencyResolver(List.of(contract, concrete));

        assertEquals(concrete, resolver.resolve(Formatter.class));
        assertThrows(MissingBeanException.class, () -> resolver.resolve(PlainFormatter.class));
    }

    @Test
    void rejectsDuplicateKeysEvenWhenTypesDiffer() {
        var first = this.definition(BracketFormatter.class, "same", "first", false);
        var second = this.definition(PlainFormatter.class, "same", "second", false);

        assertThrows(DefinitionException.class, () -> new DependencyResolver(List.of(first, second)));
    }

    @Test
    void prefersOwnSourceOverExternalPrimaryForSingleTargetDependencies() {
        var local = this.inSource(this.definition(PlainFormatter.class, "local", "", false), "a");
        var external = this.inSource(this.definition(BracketFormatter.class, "external", "", true), "b");
        for (var order : List.of(List.of(local, external), List.of(external, local))) {
            var resolver = new DependencyResolver(order);
            for (var kind : List.of(DependencyKind.DIRECT, DependencyKind.OPTIONAL, DependencyKind.PROVIDER, DependencyKind.LAZY)) {
                assertEquals(List.of(local), resolver.resolve(this.request(kind, ""), "a"));
                assertEquals(List.of(external), resolver.resolve(this.request(kind, ""), "b"));
            }
            assertEquals(external, resolver.resolve(Formatter.class));
            assertEquals(List.of(external), resolver.resolve(this.request(DependencyKind.DIRECT, ""), "other"));
        }
    }

    @Test
    void rejectsLocalAmbiguityEvenWhenAnExternalPrimaryExists() {
        var first = this.inSource(this.definition(PlainFormatter.class, "first", "", false), "a");
        var second = this.inSource(this.definition(BracketFormatter.class, "second", "", false), "a");
        var external = this.inSource(this.definition(BracketFormatter.class, "external", "", true), "b");
        var resolver = new DependencyResolver(List.of(first, second, external));

        var failure = assertThrows(AmbiguousBeanException.class,
                () -> resolver.resolve(this.request(DependencyKind.DIRECT, ""), "a"));
        assertTrue(failure.getMessage().contains(first.key().toString()));
        assertTrue(failure.getMessage().contains(second.key().toString()));
        assertFalse(failure.getMessage().contains(external.key().toString()));
    }

    @Test
    void selectsPrimaryWithinOwnSourceAndRejectsMultipleLocalPrimaries() {
        var first = this.inSource(this.definition(PlainFormatter.class, "first", "", false), "a");
        var primary = this.inSource(this.definition(BracketFormatter.class, "primary", "", true), "a");
        var external = this.inSource(this.definition(BracketFormatter.class, "external", "", true), "b");
        var resolver = new DependencyResolver(List.of(first, primary, external));
        assertEquals(List.of(primary), resolver.resolve(this.request(DependencyKind.DIRECT, ""), "a"));

        var anotherPrimary = this.inSource(this.definition(PlainFormatter.class, "another", "", true), "a");
        var ambiguous = new DependencyResolver(List.of(primary, anotherPrimary, external));
        assertThrows(AmbiguousBeanException.class,
                () -> ambiguous.resolve(this.request(DependencyKind.DIRECT, ""), "a"));
    }

    @Test
    void preservesExplicitNamesKeysAndDefaultsOverLocalPreference() {
        var local = this.inSource(this.definition(PlainFormatter.class, "local", "shared", false), "a");
        var external = this.inSource(this.definition(BracketFormatter.class, "external", "shared", true), "b");
        var resolver = new DependencyResolver(List.of(local, external));
        assertEquals(List.of(external), resolver.resolve(this.request(DependencyKind.DIRECT, "shared"), "a"));
        var keyed = new DependencyDescriptor(TypeRef.of(Formatter.class), "", DependencyKind.DIRECT,
                new InjectionPoint("keyed fixture"), null, external.key());
        assertEquals(List.of(external), resolver.resolve(keyed, "a"));

        var withDefault = new DependencyResolver(List.of(local, external),
                List.of(new SourceDefault("a", TypeRef.of(Formatter.class), external.key())));
        assertEquals(List.of(external), withDefault.resolve(this.request(DependencyKind.DIRECT, ""), "a"));
    }

    @Test
    void collectionsKeepAllSourcesAndContextFreeLookupRemainsAmbiguous() {
        var local = this.inSource(this.definition(PlainFormatter.class, "local", "", false), "a");
        var external = this.inSource(this.definition(BracketFormatter.class, "external", "", false), "b");
        var resolver = new DependencyResolver(List.of(external, local));
        for (var kind : List.of(DependencyKind.LIST, DependencyKind.SET)) {
            assertEquals(List.of(local, external), resolver.resolve(this.request(kind, ""), "a"));
        }
        assertThrows(AmbiguousBeanException.class, () -> resolver.resolve(Formatter.class));
        assertThrows(AmbiguousBeanException.class,
                () -> resolver.resolve(this.request(DependencyKind.DIRECT, ""), "other"));
    }

    private BeanDefinition inSource(BeanDefinition definition, String sourceId) {
        var source = new BeanSource(sourceId, definition.source().classLoader());
        return new BeanDefinition(new BeanKey(sourceId, definition.key().localId()), definition.type(), definition.name(),
                definition.scope(), definition.lazy(), definition.primary(), source, definition.factory());
    }

    private BeanDefinition definition(Class<?> type, String id, String name, boolean primary) {
        var base = BeanDefinitions.of(type);
        return new BeanDefinition(new BeanKey(base.source().id(), id), base.type(), name, base.scope(), false, primary,
                base.source(), base.factory());
    }

    private DependencyDescriptor request(DependencyKind kind, String name) {
        return new DependencyDescriptor(TypeRef.of(Formatter.class), name, kind, new InjectionPoint("resolver fixture"));
    }

}
