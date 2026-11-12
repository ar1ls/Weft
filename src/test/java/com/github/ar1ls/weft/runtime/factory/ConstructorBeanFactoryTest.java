package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.DependencyKind;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.fixtures.AmbiguousConstructorBean;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.InjectedConstructorBean;
import com.github.ar1ls.weft.runtime.fixtures.MultipleInjectConstructorsBean;
import com.github.ar1ls.weft.runtime.fixtures.NamedDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstructorBeanFactoryTest {

    @Test
    void rejectsUnexpectedArguments() {
        var factory = new ConstructorBeanFactory(SimpleBean.class);
        assertThrows(IllegalArgumentException.class, () -> factory.create(new Object[]{"unexpected"}));
    }

    @Test
    void describesConstructorDependency() {
        var factory = new ConstructorBeanFactory(ConstructorDependentBean.class);

        var dependencies = factory.dependencies();

        assertEquals(1, dependencies.size());
        assertEquals(TypeRef.of(SimpleBean.class), dependencies.getFirst().type());
        assertEquals(DependencyKind.DIRECT, dependencies.getFirst().kind());
        assertTrue(dependencies.getFirst().injectionPoint().description().contains("parameter 0"));
        assertThrows(UnsupportedOperationException.class, dependencies::clear);
    }

    @Test
    void passesArgumentsToSelectedConstructor() throws Exception {
        var factory = new ConstructorBeanFactory(ConstructorDependentBean.class);
        var dependency = new SimpleBean();

        var bean = (ConstructorDependentBean) factory.create(new Object[]{dependency});

        assertSame(dependency, bean.dependency());
    }

    @Test
    void selectsInjectConstructorWhenSeveralExist() throws Exception {
        var factory = new ConstructorBeanFactory(InjectedConstructorBean.class);
        var dependency = new SimpleBean();

        var bean = (InjectedConstructorBean) factory.create(new Object[]{dependency});

        assertSame(dependency, bean.getDependency());
    }

    @Test
    void rejectsAmbiguousConstructors() {
        var exception = assertThrows(DefinitionException.class,
                () -> new ConstructorBeanFactory(AmbiguousConstructorBean.class));

        assertTrue(exception.getMessage().contains(AmbiguousConstructorBean.class.getName()));
    }

    @Test
    void rejectsSeveralInjectConstructors() {
        assertThrows(DefinitionException.class, () -> new ConstructorBeanFactory(MultipleInjectConstructorsBean.class));
    }

    @Test
    void capturesNamedParameterWithoutIgnoringIt() {
        var factory = new ConstructorBeanFactory(NamedDependentBean.class);

        assertEquals("selected", factory.dependencies().getFirst().name());
    }

}
