package com.github.ar1ls.weft.runtime.fixtures.completion;

import java.util.concurrent.atomic.AtomicInteger;

public record ClosingProbe(AtomicInteger closed) implements AutoCloseable {

    @Override
    public void close() {
        this.closed.incrementAndGet();
    }

}
