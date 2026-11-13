package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.exception.BeanScannerException;
import lombok.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public class BeanScanner {

    public List<Class<?>> scan(@NonNull Path path, @NonNull ClassLoader classLoader) {
        return this.scan(path, classLoader, null);
    }

    public List<Class<?>> scan(@NonNull Path path, ClassLoader classLoader, String packageName) {
        return this.scan(path, classLoader, packageName, new String[0]);
    }

    public List<Class<?>> scan(@NonNull Path path, @NonNull ClassLoader classLoader, String packageName, String... excludePackages) {
        var found = new ArrayList<Class<?>>();
        try {
            if (Files.isDirectory(path)) {
                this.scanDirectory(path, classLoader, packageName, excludePackages, found);
            } else if (Files.isRegularFile(path)) {
                this.scanJar(path, classLoader, packageName, excludePackages, found);
            } else {
                throw new BeanScannerException("Not a directory or JAR file: " + path);
            }
        } catch (IOException | UncheckedIOException e) {
            throw new BeanScannerException("Failed to scan " + path, e);
        }
        found.sort(Comparator.comparing(Class::getName));
        return found;
    }

    private void scanDirectory(Path path, ClassLoader classLoader, String packageName, String[] excludes,
            List<Class<?>> found) throws IOException {
        try (var stream = Files.walk(path)) {
            var files = stream.iterator();
            while (files.hasNext()) {
                var file = files.next();
                if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".class")) {
                    continue;
                }
                var name = this.toClassName(path, file);
                if (!this.matches(name, packageName, excludes)) {
                    continue;
                }
                try (var bytecode = Files.newInputStream(file)) {
                    this.readCandidate(path, name, bytecode, classLoader, found);
                }
            }
        }
    }

    private void scanJar(Path path, ClassLoader classLoader, String packageName, String[] excludes,
            List<Class<?>> found) throws IOException {
        try (var jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, Runtime.version());
             var stream = jar.versionedStream()) {
            var entries = stream.iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                var entryName = entry.getName();
                if (entry.isDirectory() || entryName.startsWith("META-INF/") || !entryName.endsWith(".class")) {
                    continue;
                }
                var name = entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
                if (!this.matches(name, packageName, excludes)) {
                    continue;
                }
                try (var bytecode = jar.getInputStream(entry)) {
                    this.readCandidate(path, name, bytecode, classLoader, found);
                }
            }
        }
    }

    private boolean matches(String name, String packageName, String[] excludes) {
        return this.isUserClass(name) && this.matchesPackage(name, packageName) && !this.matchesAny(name, excludes);
    }

    private void readCandidate(Path path, String name, InputStream bytecode, ClassLoader classLoader,
            List<Class<?>> found) throws IOException {
        boolean candidate;
        try {
            candidate = ClassMetadataReader.isCandidate(bytecode, name);
        } catch (IOException | RuntimeException e) {
            throw new BeanScannerException("Cannot read class metadata: " + name + " from " + path, e);
        }
        if (candidate) {
            var type = this.loadClass(name, classLoader, path);
            if ((type.isAnnotationPresent(Bean.class) || type.isAnnotationPresent(Configuration.class))
                    && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                found.add(type);
            }
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

    private Class<?> loadClass(String name, ClassLoader classLoader, Path path) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new BeanScannerException("Cannot load class: " + name + " from " + path, e);
        }
    }

}
