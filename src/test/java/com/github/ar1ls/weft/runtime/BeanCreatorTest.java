package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
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

}
