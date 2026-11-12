package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.plan.CreationPlan;
import lombok.NonNull;

public record ManagedBean(@NonNull CreationPlan plan, @NonNull Object instance) {
}
