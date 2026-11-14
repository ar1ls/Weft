package com.github.ar1ls.weft.sandbox;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.discovery.support.ScannerFixture;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class JarScanPlayground {

    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("weft-jar-playground-");
        var path = directory.resolve("sample.jar");
        var name = "weft.playground.generated.JarGreeting";
        try {
            ScannerFixture.jar(path, Map.of(ScannerFixture.entryName(name), ScannerFixture.bean(name, "Hello from JAR")), false);
            try (var loader = new URLClassLoader(new URL[]{path.toUri().toURL()}, JarScanPlayground.class.getClassLoader())) {
                var source = new ScanSource(path, new BeanSource("sample-jar", loader),
                        List.of("weft.playground.generated"));
                try (var runtime = Weft.builder().scan(source).start()) {
                    System.out.println("JAR beans: " + runtime.report().beans().size());
                    var type = loader.loadClass(name);
                    var bean = runtime.get(type);
                    System.out.println(type.getMethod("message").invoke(bean));
                    System.out.println("Loaded by supplied classloader: " + (type.getClassLoader() == loader));
                }
            }
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

}
