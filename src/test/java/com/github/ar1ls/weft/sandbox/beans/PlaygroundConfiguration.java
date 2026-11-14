package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Primary;

@Configuration
public class PlaygroundConfiguration {

    @Bean
    @Named("brackets")
    @Primary
    public MessageFormatter formatter() {
        return new MessageFormatter();
    }

    @Bean
    @Named("plain")
    public PlainMessageFormatter plainFormatter() {
        return new PlainMessageFormatter();
    }

}
