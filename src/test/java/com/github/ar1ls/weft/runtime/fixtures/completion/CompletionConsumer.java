package com.github.ar1ls.weft.runtime.fixtures.completion;

import com.github.ar1ls.weft.api.Provider;

public record CompletionConsumer(Repository<String> text, Provider<Repository<Integer>> number, AppSettings settings) {
}
