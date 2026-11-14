package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Named;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Bean
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FormatterCatalog {

    @Inject
    @Named("plain")
    Formatter plain;

    @Inject
    List<Formatter> all;

    @Inject
    Set<Formatter> unique;

    @Inject
    Optional<Runnable> missing;

}
