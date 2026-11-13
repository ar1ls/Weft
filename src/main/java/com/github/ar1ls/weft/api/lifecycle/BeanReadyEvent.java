package com.github.ar1ls.weft.api.lifecycle;

import com.github.ar1ls.weft.definition.BeanDefinition;

public record BeanReadyEvent(BeanDefinition definition, Object instance) {

    @Override
    public String toString() {
        return "BeanReadyEvent[key=" + this.definition.key() + "]";
    }

}
