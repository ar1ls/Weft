package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Primary;

@Bean
@Named("brackets")
@Primary
public class BracketFormatter implements Formatter {

    @Override
    public String format(String value) {
        return "[" + value + "]";
    }

}
