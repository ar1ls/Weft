package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanDefinition;
import lombok.NonNull;

import java.util.List;
import java.util.Objects;

public record CreationPlan(@NonNull BeanDefinition definition, List<ResolvedDependency> arguments) {

    public CreationPlan {
        Objects.requireNonNull(definition, "definition");
        arguments = List.copyOf(arguments);
    }

}
