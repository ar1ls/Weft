package com.github.ar1ls.weft.discovery.fixtures.probes;

import com.github.ar1ls.weft.api.annotations.Bean;

@Bean
public class StaticProbe {

    static {
        System.setProperty("weft.probe.initialized", "true");
    }
    
}