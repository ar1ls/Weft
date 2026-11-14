package com.github.ar1ls.weft.sandbox;

import com.github.ar1ls.weft.Weft;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.sandbox.beans.GreetingService;
import com.github.ar1ls.weft.sandbox.beans.DeploymentMessage;

import java.nio.file.Path;
import java.util.List;

public class PublicApiPlayground {
    public static void main(String[] args) throws Exception {
        var root = Path.of(PublicApiPlayground.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var beans = PublicApiPlayground.class.getPackageName() + ".beans";
        var source = new ScanSource(root, new BeanSource("playground", PublicApiPlayground.class.getClassLoader()),
                List.of(beans), List.of(beans + ".excluded"));
        try (var runtime = Weft.builder().scan(source).property("app.mode", "dev").property("greeting.text", "Welcome, ")
                .processor(new DeploymentMessageProcessor()).start()) {
            System.out.println(runtime.get(GreetingService.class).greet("Alex"));
            System.out.println(runtime.get(DeploymentMessage.class).text());
            System.out.println("Planned beans: " + runtime.report().beans().size());
        }
    }
}
