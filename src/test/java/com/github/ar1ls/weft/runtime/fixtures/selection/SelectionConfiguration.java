package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Primary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
public class SelectionConfiguration {

    @Bean
    @Named("brackets")
    @Primary
    public Formatter brackets() {
        return new BracketFormatter();
    }

    @Bean
    @Named("plain")
    public Formatter plain() {
        return new PlainFormatter();
    }

    @Bean
    public MethodSelectedBean selected(
            @Named("plain") Formatter named,
            Optional<MissingPort> missing,
            List<Formatter> list,
            Set<Formatter> set,
            Provider<Formatter> primary
    ) {
        return new MethodSelectedBean(named, missing, list, set, primary);
    }

}
