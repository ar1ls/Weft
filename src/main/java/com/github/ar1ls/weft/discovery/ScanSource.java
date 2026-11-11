package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.definition.BeanSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ScanSource(Path root, BeanSource source, List<String> packages) {

    public ScanSource {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(source, "source");
        packages = List.copyOf(packages);
        for (var packageName : packages) {
            if (packageName.isBlank()) {
                throw new IllegalArgumentException("Package names must not be blank");
            }
        }
    }

}
