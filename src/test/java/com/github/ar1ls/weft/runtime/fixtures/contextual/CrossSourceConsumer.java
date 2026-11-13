package com.github.ar1ls.weft.runtime.fixtures.contextual;

import com.github.ar1ls.weft.api.annotations.Named;

public record CrossSourceConsumer(@Named("factory") SourceConsumerView factory) {
}
