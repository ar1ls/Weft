package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.definition.BeanSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ScanSource(Path root, BeanSource source, List<String> packages, List<String> excludedPackages) {

    public ScanSource(Path root, BeanSource source, List<String> packages) {
        this(root, source, packages, List.of());
    }

    public ScanSource {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(source, "source");
        packages = List.copyOf(packages);
        excludedPackages = List.copyOf(excludedPackages);
        for (var packageName : packages) {
            if (packageName.isBlank()) {
                throw new IllegalArgumentException("Package names must not be blank");
            }
        }
        for (var packageName : excludedPackages) {
            if (packageName.isBlank()) {
                throw new IllegalArgumentException("Excluded package names must not be blank");
            }
        }
    }

}
