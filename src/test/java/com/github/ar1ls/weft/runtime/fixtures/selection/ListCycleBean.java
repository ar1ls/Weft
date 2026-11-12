package com.github.ar1ls.weft.runtime.fixtures.selection;

import java.util.List;

public record ListCycleBean(List<Formatter> formatters) implements Formatter {

    @Override
    public String format(String value) {
        return value;
    }

}
