package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanDefinition;
import lombok.NonNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

public record CreationPlan(
        @NonNull BeanDefinition definition,
        List<ResolvedDependency> arguments,
        List<ResolvedFieldInjection> fields,
        List<Method> postConstruct,
        List<Method> preDestroy
) {

    public CreationPlan {
        Objects.requireNonNull(definition, "definition");
        arguments = List.copyOf(arguments);
        fields = List.copyOf(fields);
        postConstruct = List.copyOf(postConstruct);
        preDestroy = List.copyOf(preDestroy);
    }

}
