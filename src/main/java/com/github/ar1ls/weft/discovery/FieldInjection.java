package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.definition.DependencyDescriptor;
import lombok.NonNull;

import java.lang.reflect.Field;

public record FieldInjection(@NonNull Field field, @NonNull DependencyDescriptor dependency) {
}
