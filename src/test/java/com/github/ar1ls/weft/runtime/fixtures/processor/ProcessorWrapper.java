package com.github.ar1ls.weft.runtime.fixtures.processor;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessorWrapper extends ProcessorTarget {

    ProcessorTarget delegate;
    String prefix;

    @Override
    public String message() {
        return this.prefix + this.delegate.message();
    }

    @Override
    public void close() {
        this.getEvents().add("wrapper:close");
        this.delegate.close();
    }

}
