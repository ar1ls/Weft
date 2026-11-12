package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.exception.BeanScannerException;
import com.github.ar1ls.weft.discovery.fixtures.AnnotatedBean;
import com.github.ar1ls.weft.discovery.fixtures.configurations.AnnotatedConfiguration;
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
        var found = this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES);
        assertTrue(simpleNames(found).contains("AnnotatedBean"));
    }

    @Test
    void findsConfigurationWithoutBeanAnnotation() throws Exception {
        var found = this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".configurations");

        assertEquals(List.of(AnnotatedConfiguration.class), found);
    }

    @Test
    void ignoresClassesWithoutBeanAnnotation() throws Exception {
        var found = this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".plain");
        assertTrue(found.isEmpty(), "JustPlain не должен находиться");
    }

    @Test
    void scanWithoutPackageFindsAll() throws Exception {
        var names = simpleNames(this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader()));
        assertTrue(names.contains("AnnotatedBean"));
        assertTrue(names.contains("SiblingBean"));
    }

    /**
     * Package filter
     */

    @Test
    void respectsPackageFilter() throws Exception {
        var names = simpleNames(
                this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".sibling"));
        assertTrue(names.contains("SiblingBean"));
        assertFalse(names.contains("AnnotatedBean"),
                "class from another package should not be found");
    }

    @Test
    void excludesPackages() throws Exception {
        var names = simpleNames(this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES,
                FIXTURES + ".excluded"));
        assertFalse(names.contains("ExcludedBean"));
        assertTrue(names.contains("AnnotatedBean"));
    }

    /**
     * Abstract and interface filtering
     */

    @Test
    void abstractAnnotatedClassIsFiltered() throws Exception {
        assertTrue(this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".abstracts").isEmpty());
    }

    @Test
    void annotatedInterfaceIsFiltered() throws Exception {
        assertTrue(this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".interfaces").isEmpty());
    }

    /**
     * Contract tests
     */

    @Test
    void missingDirectoryThrows() {
        var ex = assertThrows(BeanScannerException.class,
                () -> this.scanner.scan(Path.of("does/not/exist"), BeanScannerTest.class.getClassLoader()));
        assertTrue(ex.getMessage().contains("Not a directory"));
    }

    @Test
    void returnsEmptyListNotNull() throws Exception {
        var found = this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".plain");
        assertNotNull(found);
        assertTrue(found.isEmpty());
    }

    @Test
    void resultIsSortedByName() throws Exception {
        var names = this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES).stream()
                .map(Class::getName).toList();
        assertEquals(names.stream().sorted().toList(), names);
    }

    /**
     * Static initializer is not invoked
     */

    @Test
    void staticInitializerIsNotInvoked() throws Exception {
        System.clearProperty("weft.probe.initialized");
        var found = this.scanner.scan(testRoot(), BeanScannerTest.class.getClassLoader(), FIXTURES + ".probes");
        assertFalse(found.isEmpty(), "class should be found");
        assertNull(System.getProperty("weft.probe.initialized"));
    }

}
