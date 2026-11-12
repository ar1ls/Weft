package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.BeanCreator;
import com.github.ar1ls.weft.runtime.fixtures.BeanDefinitions;
import com.github.ar1ls.weft.runtime.fixtures.ConstructorDependentBean;
import com.github.ar1ls.weft.runtime.fixtures.MethodFactoryFixtures;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodBeanFactoryTest {

    @Test
    void declaresOwnerBeforeMethodParameters() throws Exception {
        var method = MethodFactoryFixtures.class.getMethod("dependent", SimpleBean.class);
        var factory = new MethodBeanFactory(method, MethodFactoryFixtures.class);

        assertEquals(2, factory.dependencies().size());
        assertEquals(TypeRef.of(MethodFactoryFixtures.class), factory.dependencies().get(0).type());
        assertEquals(TypeRef.of(SimpleBean.class), factory.dependencies().get(1).type());
        assertTrue(factory.dependencies().get(0).injectionPoint().description().contains("configuration owner"));
    }

    @Test
    void invokesInstanceMethodWithSuppliedOwnerAndArguments() throws Exception {
        var method = MethodFactoryFixtures.class.getMethod("dependent", SimpleBean.class);
        var factory = new MethodBeanFactory(method, MethodFactoryFixtures.class);
        var owner = new MethodFactoryFixtures();
        var dependency = new SimpleBean();

        var bean = (ConstructorDependentBean) factory.create(new Object[]{owner, dependency});

        assertSame(dependency, bean.dependency());
    }

    @Test
    void invokesStaticMethodWithoutOwner() throws Exception {
        var method = MethodFactoryFixtures.class.getMethod("simple");
        var factory = new MethodBeanFactory(method, MethodFactoryFixtures.class);

        assertTrue(factory.dependencies().isEmpty());
        assertInstanceOf(SimpleBean.class, factory.create(new Object[0]));
    }

    @Test
    void rejectsVoidPrimitiveAndNonPublicMethods() throws Exception {
        var voidMethod = MethodFactoryFixtures.class.getMethod("noReturn");
        var primitive = MethodFactoryFixtures.class.getMethod("primitive");
        var hidden = MethodFactoryFixtures.class.getDeclaredMethod("hidden");

        assertThrows(DefinitionException.class, () -> new MethodBeanFactory(voidMethod, MethodFactoryFixtures.class));
        assertThrows(DefinitionException.class, () -> new MethodBeanFactory(primitive, MethodFactoryFixtures.class));
        assertThrows(DefinitionException.class, () -> new MethodBeanFactory(hidden, MethodFactoryFixtures.class));
    }

    @Test
    void rejectsWrongOwnerAndArgumentCount() throws Exception {
        var method = MethodFactoryFixtures.class.getMethod("dependent", SimpleBean.class);
        var factory = new MethodBeanFactory(method, MethodFactoryFixtures.class);

        assertThrows(IllegalArgumentException.class, () -> factory.create(new Object[0]));
        assertThrows(IllegalArgumentException.class, () -> factory.create(new Object[]{new Object(), new SimpleBean()}));
        assertThrows(DefinitionException.class, () -> new MethodBeanFactory(method, SimpleBean.class));
    }

    @Test
    void creatorRejectsNullMethodResult() throws Exception {
        var method = MethodFactoryFixtures.class.getMethod("nullBean");
        var factory = new MethodBeanFactory(method, MethodFactoryFixtures.class);
        var definition = BeanDefinitions.of(SimpleBean.class, factory);

        var exception = assertThrows(BeanCreationException.class, () -> new BeanCreator().create(definition));

        assertTrue(exception.getMessage().contains(definition.key().toString()));
    }

    @Test
    void creatorPreservesOriginalMethodFailure() throws Exception {
        var method = MethodFactoryFixtures.class.getMethod("throwingBean");
        var factory = new MethodBeanFactory(method, MethodFactoryFixtures.class);
        var definition = BeanDefinitions.of(SimpleBean.class, factory);

        var exception = assertThrows(BeanCreationException.class, () -> new BeanCreator().create(definition));

        var cause = assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Method factory probe", cause.getMessage());
    }

}
