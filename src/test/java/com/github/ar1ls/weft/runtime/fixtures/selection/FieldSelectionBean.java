package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Named;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FieldSelectionBean {

    @Inject
    Formatter primary;

    @Inject
    @Named("plain")
    Formatter named;

    @Inject
    @Named("plain")
    Provider<Formatter> provider;

    @Inject
    Optional<MissingPort> missing;

    @Inject
    List<Formatter> list;

    @Inject
    Set<Formatter> set;

}
