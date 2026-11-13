package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.exception.BeanScannerException;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.support.RecordingClassLoader;
import com.github.ar1ls.weft.discovery.support.ScannerFixture;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.BeanRegistry;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JarBeanScannerTest {

    private static final String PACKAGE = "weft.scanner.generated";

    @TempDir
    Path temporary;

    @Test
    void findsOnlyConcreteCandidatesAndSortsWithoutInitializing() throws Exception {
        var bean = PACKAGE + ".ZBean";
        var configuration = PACKAGE + ".AConfiguration";
        var probe = PACKAGE + ".Probe";
        var plain = PACKAGE + ".Plain";
        var abstractBean = PACKAGE + ".AbstractBean";
        var beanInterface = PACKAGE + ".BeanInterface";
        var nested = PACKAGE + ".Owner$Nested";
        var classes = Map.of(bean, ScannerFixture.bean(bean, "bean"),
                configuration, ScannerFixture.configuration(configuration),
                probe, ScannerFixture.initializationProbe(probe),
                plain, ScannerFixture.brokenSuperclass(plain, false),
                abstractBean, ScannerFixture.abstractBean(abstractBean),
                beanInterface, ScannerFixture.beanInterface(beanInterface),
                nested, ScannerFixture.bean(nested, "nested"));
        var entries = this.entries(classes);
        entries.put("module-info.class", new byte[]{0});
        entries.put("weft/scanner/generated/package-info.class", new byte[]{0});
        entries.put("META-INF/ignored.class", new byte[]{0});
        var path = ScannerFixture.jar(this.temporary.resolve("fixtures.jar"), entries, false);
        var loader = new RecordingClassLoader(classes);
        var found = new BeanScanner().scan(path, loader, PACKAGE);
        assertEquals(List.of(configuration, probe, bean), this.names(found));
        assertFalse(loader.requestedClasses().contains(plain));
        assertFalse(loader.requestedClasses().contains(abstractBean));
        assertFalse(loader.requestedClasses().contains(beanInterface));
        assertFalse(loader.requestedClasses().contains(nested));
        for (var type : found) {
            assertSame(loader, type.getClassLoader());
        }
        var failure = assertThrows(ExceptionInInitializerError.class, () -> Class.forName(probe, true, loader));
        assertEquals("Scanner initialized probe", failure.getCause().getMessage());
        Files.delete(path);
        assertFalse(Files.exists(path));
    }

    @Test
    void appliesPackageBoundariesAndExclusionsBeforeReadingBytes() throws Exception {
        var selected = PACKAGE + ".Selected";
        var sibling = PACKAGE + "extra.Sibling";
        var excluded = PACKAGE + ".excluded.Broken";
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put(ScannerFixture.entryName(selected), ScannerFixture.bean(selected, "selected"));
        entries.put(ScannerFixture.entryName(sibling), new byte[]{0});
        entries.put(ScannerFixture.entryName(excluded), new byte[]{0});
        var path = ScannerFixture.jar(this.temporary.resolve("filters.jar"), entries, false);
        var loader = new RecordingClassLoader(Map.of(selected, ScannerFixture.bean(selected, "selected")));
        var found = new BeanScanner().scan(path, loader, PACKAGE, PACKAGE + ".excluded");
        assertEquals(List.of(selected), this.names(found));
        assertEquals(List.of(selected), loader.requestedClasses());
    }

    @Test
    void prefiltersDirectoryClassesWithTheSameMetadataRules() throws Exception {
        var bean = PACKAGE + ".Bean";
        var plain = PACKAGE + ".Plain";
        var classes = Map.of(bean, ScannerFixture.bean(bean, "directory"), plain, ScannerFixture.brokenSuperclass(plain, false));
        for (var entry : this.entries(classes).entrySet()) {
            var path = this.temporary.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, entry.getValue());
        }
        var loader = new RecordingClassLoader(classes);
        assertEquals(List.of(bean), this.names(new BeanScanner().scan(this.temporary, loader, PACKAGE)));
        assertEquals(List.of(bean), loader.requestedClasses());
    }

    @Test
    void integratesJarClassesWithReaderAndRuntimeWithoutClosingCallerLoader() throws Exception {
        var name = PACKAGE + ".JarBean";
        var entries = Map.of(ScannerFixture.entryName(name), ScannerFixture.bean(name, "Hello from JAR"),
                "scanner-owned-resource.txt", new byte[]{42});
        var path = ScannerFixture.jar(this.temporary.resolve("runtime.jar"), entries, false);
        try (var loader = new URLClassLoader(new URL[]{path.toUri().toURL()}, this.getClass().getClassLoader())) {
            var type = new BeanScanner().scan(path, loader, PACKAGE).getFirst();
            assertSame(loader, type.getClassLoader());
            var registry = new BeanRegistry();
            var properties = new PropertyResolver(Map.of(), Map.of(), Map.of());
            var reader = new AnnotationDefinitionReader(properties);
            for (var definition : reader.read(type, new BeanSource("jar", loader))) {
                registry.register(definition);
            }
            try (var runtime = new WeftRuntime(registry, properties)) {
                runtime.start();
                var bean = runtime.get(type);
                assertEquals("Hello from JAR", type.getMethod("message").invoke(bean));
            }
            try (var resource = loader.getResourceAsStream("scanner-owned-resource.txt")) {
                assertNotNull(resource);
                assertEquals(42, resource.read());
            }
        }
        Files.delete(path);
    }

    @Test
    void sameBinaryNameInDifferentLoadersRemainsDifferentTypes() throws Exception {
        var name = PACKAGE + ".Isolated";
        var first = ScannerFixture.jar(this.temporary.resolve("first.jar"),
                Map.of(ScannerFixture.entryName(name), ScannerFixture.bean(name, "first")), false);
        var second = ScannerFixture.jar(this.temporary.resolve("second.jar"),
                Map.of(ScannerFixture.entryName(name), ScannerFixture.bean(name, "second")), false);
        try (var firstLoader = new URLClassLoader(new URL[]{first.toUri().toURL()}, this.getClass().getClassLoader());
             var secondLoader = new URLClassLoader(new URL[]{second.toUri().toURL()}, this.getClass().getClassLoader())) {
            var scanner = new BeanScanner();
            var firstType = scanner.scan(first, firstLoader).getFirst();
            var secondType = scanner.scan(second, secondLoader).getFirst();
            assertNotSame(firstType, secondType);
            assertFalse(firstType.isAssignableFrom(secondType));
            assertEquals("first", firstType.getMethod("message").invoke(firstType.getConstructor().newInstance()));
            assertEquals("second", secondType.getMethod("message").invoke(secondType.getConstructor().newInstance()));
        }
    }

    @Test
    void selectsRuntimeVersionOnceInMultiReleaseJar() throws Exception {
        var name = PACKAGE + ".Versioned";
        var entries = Map.of(ScannerFixture.entryName(name), ScannerFixture.brokenSuperclass(name, false),
                "META-INF/versions/9/" + ScannerFixture.entryName(name), ScannerFixture.bean(name, "runtime"),
                "META-INF/versions/999/" + ScannerFixture.entryName(name), new byte[]{0});
        var path = ScannerFixture.jar(this.temporary.resolve("versions.jar"), entries, true);
        try (var loader = new URLClassLoader(new URL[]{path.toUri().toURL()}, this.getClass().getClassLoader())) {
            var found = new BeanScanner().scan(path, loader);
            assertEquals(List.of(name), this.names(found));
            var type = found.getFirst();
            assertEquals("runtime", type.getMethod("message").invoke(type.getConstructor().newInstance()));
        }
    }

    @Test
    void reportsBrokenCandidateWithClassAndSourceInsteadOfSilentlySkipping() throws Exception {
        var name = PACKAGE + ".BrokenBean";
        var classes = Map.of(name, ScannerFixture.brokenSuperclass(name, true));
        var path = ScannerFixture.jar(this.temporary.resolve("broken.jar"), this.entries(classes), false);
        var failure = assertThrows(BeanScannerException.class,
                () -> new BeanScanner().scan(path, new RecordingClassLoader(classes)));
        assertTrue(failure.getMessage().contains(name));
        assertTrue(failure.getMessage().contains(path.toString()));
        assertInstanceOf(LinkageError.class, failure.getCause());
        Files.delete(path);
    }

    @Test
    void reportsMalformedArchiveAndMetadataAndMissingPath() throws Exception {
        var loader = new RecordingClassLoader(Map.of());
        var scanner = new BeanScanner();
        var corrupt = this.temporary.resolve("corrupt.jar");
        Files.write(corrupt, new byte[]{0});
        var archiveFailure = assertThrows(BeanScannerException.class, () -> scanner.scan(corrupt, loader));
        assertTrue(archiveFailure.getMessage().contains(corrupt.toString()));
        assertNotNull(archiveFailure.getCause());
        var name = PACKAGE + ".Malformed";
        var malformed = ScannerFixture.jar(this.temporary.resolve("malformed.jar"),
                Map.of(ScannerFixture.entryName(name), new byte[]{0}), false);
        var metadataFailure = assertThrows(BeanScannerException.class, () -> scanner.scan(malformed, loader));
        assertTrue(metadataFailure.getMessage().contains(name));
        assertTrue(metadataFailure.getMessage().contains(malformed.toString()));
        assertThrows(BeanScannerException.class, () -> scanner.scan(this.temporary.resolve("missing.jar"), loader));
        Files.delete(corrupt);
        Files.delete(malformed);
    }

    @Test
    void rejectsMismatchedClassNameBeforeAskingLoader() throws Exception {
        var name = PACKAGE + ".Expected";
        var path = ScannerFixture.jar(this.temporary.resolve("mismatch.jar"),
                Map.of(ScannerFixture.entryName(name), ScannerFixture.bean(PACKAGE + ".Actual", "wrong entry")), false);
        var loader = new RecordingClassLoader(Map.of());
        var failure = assertThrows(BeanScannerException.class, () -> new BeanScanner().scan(path, loader));
        assertTrue(failure.getMessage().contains(name));
        assertTrue(loader.requestedClasses().isEmpty());
        Files.delete(path);
    }

    @Test
    void missingClassInCallerLoaderIsReportedAndEmptyJarIsAllowed() throws Exception {
        var scanner = new BeanScanner();
        var loader = new RecordingClassLoader(Map.of());
        var empty = ScannerFixture.jar(this.temporary.resolve("empty.jar"), Map.of(), false);
        assertTrue(scanner.scan(empty, loader).isEmpty());
        var name = PACKAGE + ".NotVisible";
        var path = ScannerFixture.jar(this.temporary.resolve("not-visible.jar"),
                Map.of(ScannerFixture.entryName(name), ScannerFixture.bean(name, "not visible")), false);
        var failure = assertThrows(BeanScannerException.class, () -> scanner.scan(path, loader));
        assertInstanceOf(ClassNotFoundException.class, failure.getCause());
        assertTrue(failure.getMessage().contains(name));
        assertTrue(failure.getMessage().contains(path.toString()));
    }

    private Map<String, byte[]> entries(Map<String, byte[]> classes) {
        var entries = new LinkedHashMap<String, byte[]>();
        for (var entry : classes.entrySet()) {
            entries.put(ScannerFixture.entryName(entry.getKey()), entry.getValue());
        }
        return entries;
    }

    private List<String> names(List<Class<?>> classes) {
        var names = new ArrayList<String>();
        for (var type : classes) {
            names.add(type.getName());
        }
        return names;
    }

}
