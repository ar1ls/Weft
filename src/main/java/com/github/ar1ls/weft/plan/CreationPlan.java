package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import lombok.NonNull;

import java.util.List;
import java.util.Objects;

public record CreationPlan(@NonNull BeanDefinition definition, List<BeanKey> arguments) {

    public CreationPlan {
        Objects.requireNonNull(definition, "definition");
        arguments = List.copyOf(arguments);
    }

}
