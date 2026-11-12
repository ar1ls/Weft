package com.github.ar1ls.weft.plan;

import lombok.NonNull;

import java.lang.reflect.Field;

public record ResolvedFieldInjection(@NonNull Field field, @NonNull ResolvedDependency dependency) {
}
