package com.github.ar1ls.weft.runtime.factory;

import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstructorBeanFactoryTest {

    @Test
    void rejectsUnexpectedArguments() {
        var factory = new ConstructorBeanFactory(SimpleBean.class);
        assertThrows(IllegalArgumentException.class, () -> factory.create(new Object[]{"unexpected"}));
    }

}
