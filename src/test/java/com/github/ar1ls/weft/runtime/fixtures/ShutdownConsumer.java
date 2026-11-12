package com.github.ar1ls.weft.runtime.fixtures;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShutdownConsumer extends ShutdownResource {

    @Getter
    ShutdownResource resource;

    public ShutdownConsumer(ShutdownResource resource, List<String> events) {
        super("consumer", events);
        this.resource = resource;
    }

}
