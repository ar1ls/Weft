package com.github.ar1ls.weft.runtime.fixtures.completion;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExternalConsumer {

    @Inject
    SimpleBean dependency;

    boolean initialized;

    @PostConstruct
    private void initialize() {
        this.initialized = true;
    }

}
