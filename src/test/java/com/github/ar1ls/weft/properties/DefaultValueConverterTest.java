package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.api.exception.PropertyBindingException;
import com.github.ar1ls.weft.runtime.fixtures.properties.BindingMode;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultValueConverterTest {

    @Test
    void convertsSupportedScalarTypesWithoutTrimmingStrings() {
        assertEquals("  text  ", DefaultValueConverter.convert("key", "  text  ", String.class));
        assertEquals("", DefaultValueConverter.convert("key", "", String.class));
        assertEquals(' ', DefaultValueConverter.convert("key", " ", char.class));
        assertEquals('X', DefaultValueConverter.convert("key", "X", Character.class));
        for (var type : List.of(boolean.class, Boolean.class)) {
            assertEquals(true, DefaultValueConverter.convert("key", " TRUE ", type));
            assertEquals(false, DefaultValueConverter.convert("key", "false", type));
        }
        for (var type : List.of(byte.class, Byte.class)) {
            assertEquals((byte) 12, DefaultValueConverter.convert("key", "12", type));
        }
        for (var type : List.of(short.class, Short.class)) {
            assertEquals((short) 12, DefaultValueConverter.convert("key", "12", type));
        }
        for (var type : List.of(int.class, Integer.class)) {
            assertEquals(12, DefaultValueConverter.convert("key", " 12 ", type));
        }
        for (var type : List.of(long.class, Long.class)) {
            assertEquals(12L, DefaultValueConverter.convert("key", "12", type));
        }
        for (var type : List.of(float.class, Float.class)) {
            assertEquals(1.5F, DefaultValueConverter.convert("key", "1.5", type));
        }
        for (var type : List.of(double.class, Double.class)) {
            assertEquals(1.5D, DefaultValueConverter.convert("key", "1.5", type));
        }
        assertEquals(BindingMode.LOCAL, DefaultValueConverter.convert("key", "LOCAL", BindingMode.class));
        assertEquals(Duration.ofSeconds(5), DefaultValueConverter.convert("key", "PT5S", Duration.class));
        assertEquals(Path.of("config"), DefaultValueConverter.convert("key", "config", Path.class));
    }

    @Test
    void rejectsInvalidValuesAndOverflowInsteadOfSilentlyCoercing() {
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "yes", boolean.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "128", byte.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "", int.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "2147483648", int.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "NaN", float.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "Infinity", double.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "ab", char.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "local", BindingMode.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "5 seconds", Duration.class));
        assertThrows(PropertyBindingException.class, () -> DefaultValueConverter.convert("key", "one,two", List.class));
    }

    @Test
    void excludesRawValuesAndParserCausesFromDiagnostics() {
        var secret = "do-not-print-this-value";
        var exception = assertThrows(PropertyBindingException.class,
                () -> DefaultValueConverter.convert("app.limit", secret, int.class));
        var output = new StringWriter();
        exception.printStackTrace(new PrintWriter(output));

        assertFalse(output.toString().contains(secret));
        assertTrue(exception.getMessage().contains("app.limit"));
        assertTrue(exception.getMessage().contains("int"));
        assertNull(exception.getCause());
    }

    @Test
    void convertsDefaultInt() {
        assertEquals(
                8,
                DefaultValueConverter.convert(
                        "CFG_S3_THREADS",
                        "8",
                        int.class
                )
        );
    }

}
