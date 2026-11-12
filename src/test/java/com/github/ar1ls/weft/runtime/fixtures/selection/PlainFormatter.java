package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Named;

@Bean
@Named("plain")
public class PlainFormatter implements Formatter {

    @Override
    public String format(String value) {
        return value;
    }

}
