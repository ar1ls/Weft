package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyDescriptor;

import java.util.List;
import java.util.Objects;

public record ResolvedDependency(DependencyDescriptor descriptor, List<BeanKey> targets) {

    public ResolvedDependency {
        Objects.requireNonNull(descriptor, "descriptor");
        targets = List.copyOf(targets);
        var valid = switch (descriptor.kind()) {
            case DIRECT, PROVIDER -> targets.size() == 1;
            case OPTIONAL -> targets.size() <= 1;
            case LIST, SET -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid target count at " + descriptor.injectionPoint().description());
        }
    }

}
