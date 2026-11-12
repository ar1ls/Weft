package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FieldShutdownConsumer extends ShutdownResource {

    @Inject
    ShutdownResource resource;

    public FieldShutdownConsumer(List<String> events) {
        super("field", events);
    }

}
