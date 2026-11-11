package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.exception.BeanScannerException;
import com.github.ar1ls.weft.discovery.fixtures.AnnotatedBean;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
class BeanScannerTest {

    private static final String FIXTURES = "com.github.ar1ls.weft.discovery.fixtures";

    BeanScanner scanner = new BeanScanner();

    private static Path testRoot() throws Exception {
        return Path.of(AnnotatedBean.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
    }

    private static List<String> simpleNames(List<Class<?>> classes) {
        return classes.stream().map(Class::getSimpleName).toList();
    }

    /**
     * Base behavior
     */

    @Test
    void findsAnnotatedClassesInPackage() throws Exception {
        var found = this.scanner.scan(testRoot(), FIXTURES);
        assertTrue(simpleNames(found).contains("AnnotatedBean"));
    }

    @Test
    void ignoresClassesWithoutBeanAnnotation() throws Exception {
        var found = this.scanner.scan(testRoot(), FIXTURES + ".plain");
        assertTrue(found.isEmpty(), "JustPlain не должен находиться");
    }

    @Test
    void scanWithoutPackageFindsAll() throws Exception {
        var names = simpleNames(this.scanner.scan(testRoot()));
        assertTrue(names.contains("AnnotatedBean"));
        assertTrue(names.contains("SiblingBean"));
    }

    /**
     * Package filter
     */

    @Test
    void respectsPackageFilter() throws Exception {
        var names = simpleNames(
                this.scanner.scan(testRoot(), FIXTURES + ".sibling"));
        assertTrue(names.contains("SiblingBean"));
        assertFalse(names.contains("AnnotatedBean"),
                "класс из другого пакета не должен попасть");
    }

    @Test
    void excludesPackages() throws Exception {
        var names = simpleNames(this.scanner.scan(testRoot(), FIXTURES,
                FIXTURES + ".excluded"));
        assertFalse(names.contains("ExcludedBean"));
        assertTrue(names.contains("AnnotatedBean"));
    }

    /**
     * Abstract and interface filtering
     */

    @Test
    void abstractAnnotatedClassIsFiltered() throws Exception {
        assertTrue(this.scanner.scan(testRoot(), FIXTURES + ".abstracts").isEmpty());
    }

    @Test
    void annotatedInterfaceIsFiltered() throws Exception {
        assertTrue(this.scanner.scan(testRoot(), FIXTURES + ".interfaces").isEmpty());
    }

    /**
     * Contract tests
     */

    @Test
    void missingDirectoryThrows() {
        var ex = assertThrows(BeanScannerException.class,
                () -> this.scanner.scan(Path.of("does/not/exist")));
        assertTrue(ex.getMessage().contains("Not a directory"));
    }

    @Test
    void returnsEmptyListNotNull() throws Exception {
        var found = this.scanner.scan(testRoot(), FIXTURES + ".plain");
        assertNotNull(found);
        assertTrue(found.isEmpty());
    }

    @Test
    void resultIsSortedByName() throws Exception {
        var names = this.scanner.scan(testRoot(), FIXTURES).stream()
                .map(Class::getName).toList();
        assertEquals(names.stream().sorted().toList(), names);
    }

    /**
     * Static initializer is not invoked
     */

    @Test
    void staticInitializerIsNotInvoked() throws Exception {
        System.clearProperty("weft.probe.initialized");
        var found = this.scanner.scan(testRoot(), FIXTURES + ".probes");
        assertFalse(found.isEmpty(), "class should be found");
        assertNull(System.getProperty("weft.probe.initialized"));
    }

}