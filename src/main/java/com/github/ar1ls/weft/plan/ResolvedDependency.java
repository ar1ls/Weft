package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.DependencyDescriptor;
import lombok.NonNull;

import java.util.List;
import java.util.Objects;

public record ResolvedDependency(DependencyDescriptor descriptor, List<BeanKey> targets, Object propertyValue) {

    public ResolvedDependency(DependencyDescriptor descriptor, List<BeanKey> targets) {
        this(descriptor, targets, null);
    }

    @Override
    public @NonNull String toString() {
        return "ResolvedDependency[descriptor=" + this.descriptor + ", targets=" + this.targets + "]";
    }

    public ResolvedDependency {
        Objects.requireNonNull(descriptor, "descriptor");
        targets = List.copyOf(targets);
        var valid = switch (descriptor.kind()) {
            case DIRECT, PROVIDER, LAZY -> targets.size() == 1;
            case VALUE -> targets.isEmpty();
            case OPTIONAL -> targets.size() <= 1;
            case LIST, SET -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid target count at " + descriptor.injectionPoint().description());
        }
    }

}
