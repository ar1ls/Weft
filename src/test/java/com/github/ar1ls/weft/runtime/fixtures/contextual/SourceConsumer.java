package com.github.ar1ls.weft.runtime.fixtures.contextual;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Lazy;
import com.github.ar1ls.weft.api.annotations.Named;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SourceConsumer implements SourceConsumerView {

    final SourceLabel constructorLabel;

    @Inject
    SourceLabel label;

    @Inject
    Provider<SourceLabel> provider;

    @Inject
    Optional<SourceLabel> optional;

    @Inject
    @Lazy
    SourceLabel lazy;

    @Inject
    List<SourceLabel> labels;

    @Inject
    Set<SourceLabel> labelSet;

    @Inject
    @Named("override")
    SourceLabel overrideLabel;

}
