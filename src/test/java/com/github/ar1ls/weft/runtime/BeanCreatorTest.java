package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.runtime.fixtures.NoDefaultConstructorBean;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import com.github.ar1ls.weft.runtime.fixtures.ThrowingConstructorBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeanCreatorTest {

    @Test
    void createsBeanWithPublicNoArgConstructor() {
        var creator = new BeanCreator();
        var bean = creator.create(SimpleBean.class);
        assertNotNull(bean);
        assertEquals(SimpleBean.class, bean.getClass());
    }

    @Test
    void reportsMissingPublicNoArgConstructor() {
        var creator = new BeanCreator();
        var exception = assertThrows(BeanCreationException.class,
                () -> creator.create(NoDefaultConstructorBean.class));
        assertTrue(exception.getMessage().contains(NoDefaultConstructorBean.class.getName()));
        assertInstanceOf(NoSuchMethodException.class, exception.getCause());
    }

    @Test
    void createsNewInstanceOnEachCall() {
        var creator = new BeanCreator();
        var first = creator.create(SimpleBean.class);
        var second = creator.create(SimpleBean.class);
        assertNotSame(first, second);
    }

    @Test
    void preservesConstructorFailureCause() {
        var creator = new BeanCreator();
        var exception = assertThrows(BeanCreationException.class,
                () -> creator.create(ThrowingConstructorBean.class));
        assertTrue(exception.getMessage().contains(ThrowingConstructorBean.class.getName()));
        var cause = assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Constructor probe", cause.getMessage());
    }

}
