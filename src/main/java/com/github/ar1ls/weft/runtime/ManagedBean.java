package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.plan.CreationPlan;
import lombok.NonNull;

public record ManagedBean(@NonNull CreationPlan plan, @NonNull Object original, @NonNull Object exposed) {

    public ManagedBean(CreationPlan plan, Object original) {
        this(plan, original, original);
    }

}
