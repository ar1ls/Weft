package com.github.ar1ls.weft.spi;

import java.util.Optional;

@FunctionalInterface
public interface PropertySource {

    Optional<String> find(String key);
    
}
