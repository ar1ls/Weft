package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FailingHookBean {

    final RuntimeException failure;

    @Getter
    int attempts;

    @PostConstruct
    private void initialize() {
        this.attempts++;
        throw this.failure;
    }

}
