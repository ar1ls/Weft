package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderShutdownConsumer extends ShutdownResource {

    @Inject
    @Getter
    Provider<ShutdownResource> resource;

    public ProviderShutdownConsumer(List<String> events) {
        super("provider", events);
    }

}
