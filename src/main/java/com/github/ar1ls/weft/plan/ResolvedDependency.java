package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyDescriptor;

import java.util.Objects;

public record ResolvedDependency(DependencyDescriptor descriptor, BeanKey target) {

    public ResolvedDependency {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(target, "target");
    }

}
