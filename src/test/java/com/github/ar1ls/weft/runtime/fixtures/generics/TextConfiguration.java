package com.github.ar1ls.weft.runtime.fixtures.generics;

import com.github.ar1ls.weft.api.annotations.Configuration;

@Configuration
public class TextConfiguration extends GenericConfigurationBase<String> {

    @Override
    protected String value() {
        return "inherited";
    }

}
