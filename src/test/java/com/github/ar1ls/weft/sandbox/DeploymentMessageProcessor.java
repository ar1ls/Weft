package com.github.ar1ls.weft.sandbox;

import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.sandbox.beans.DeploymentMessage;
import com.github.ar1ls.weft.spi.BeanProcessor;

public class DeploymentMessageProcessor implements BeanProcessor {

    @Override
    public Object process(BeanDefinition definition, Object instance) {
        if (definition.type().type() != DeploymentMessage.class) {
            return instance;
        }
        var message = (DeploymentMessage) instance;
        return (DeploymentMessage) () -> "Processed: " + message.text();
    }

}
