package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.exception.BeanScannerException;
import lombok.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BeanScanner {

    public List<Class<?>> scan(@NonNull Path path, @NonNull ClassLoader classLoader) {
        return this.scan(path, classLoader, null);
    }

    public List<Class<?>> scan(@NonNull Path path, ClassLoader classLoader, String packageName) {
        return this.scan(path, classLoader, packageName, new String[0]);
    }

    public List<Class<?>> scan(@NonNull Path path, @NonNull ClassLoader classLoader, String packageName, String... excludePackages) {
        if (!Files.isDirectory(path)) {
            throw new BeanScannerException("Not a directory: " + path);
        }

        try (var stream = Files.walk(path)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .map(p -> this.toClassName(path, p))
                    .filter(this::isUserClass)
                    .filter(name -> this.matchesPackage(name, packageName))
                    .filter(name -> !this.matchesAny(name, excludePackages))
                    .map(name -> this.loadClass(name, classLoader))
                    .filter(c -> c.isAnnotationPresent(Bean.class))
                    .filter(c -> !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))
                    .sorted(Comparator.comparing(Class::getName))
                    .collect(Collectors.toList());
        } catch (IOException | UncheckedIOException e) {
            throw new BeanScannerException("Failed to scan " + path, e);
        }
    }

    private String toClassName(Path root, Path classFile) {
        var rel = root.relativize(classFile).toString();
        var noExt = rel.substring(0, rel.length() - ".class".length());
        return noExt.replace('\\', '.').replace('/', '.');
    }

    private boolean isUserClass(String name) {
        var simple = name.substring(name.lastIndexOf('.') + 1);
        if (simple.equals("module-info") || simple.equals("package-info")) {
            return false;
        }
        return !name.contains("$");
    }

    private boolean matchesPackage(String name, String pkg) {
        return pkg == null || pkg.isEmpty() || name.startsWith(pkg + ".");
    }

    private boolean matchesAny(String name, String... excludes) {
        for (var ex : excludes) {
            if (ex != null && !ex.isEmpty() && name.startsWith(ex + ".")) {
                return true;
            }
        }
        return false;
    }

    private Class<?> loadClass(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new BeanScannerException("Cannot load class: " + name, e);
        }
    }

}
