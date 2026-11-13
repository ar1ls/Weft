package com.github.ar1ls.weft.runtime.fixtures.completion;

import com.github.ar1ls.weft.api.annotations.Property;

public record Address(@Property(defaultValue = "localhost") String host, @Property(defaultValue = "25565") int port) {
}
