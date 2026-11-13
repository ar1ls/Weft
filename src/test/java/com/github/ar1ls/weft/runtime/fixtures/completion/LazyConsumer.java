package com.github.ar1ls.weft.runtime.fixtures.completion;

import com.github.ar1ls.weft.api.annotations.Lazy;

public record LazyConsumer(@Lazy Runnable task) {
}
