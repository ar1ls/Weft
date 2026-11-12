package com.github.ar1ls.weft.runtime.fixtures.selection;

import java.util.Optional;

public record OptionalCycleBean(Optional<OptionalCycleBean> self) {
}
