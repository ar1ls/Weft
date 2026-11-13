package com.github.ar1ls.weft.runtime.fixtures.processor;

import com.github.ar1ls.weft.api.Provider;

import java.util.List;
import java.util.Optional;

public record ProcessorConsumer(ProcessorTarget direct, Provider<ProcessorTarget> provider,
        List<ProcessorTarget> all, Optional<ProcessorTarget> optional) {
}
