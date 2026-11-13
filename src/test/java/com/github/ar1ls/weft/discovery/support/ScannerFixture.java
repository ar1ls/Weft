package com.github.ar1ls.weft.discovery.support;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

@UtilityClass
public class ScannerFixture {

    public byte[] bean(String name, String message) {
        return bytecode(name, "java/lang/Object", Opcodes.ACC_PUBLIC, Type.getDescriptor(Bean.class), false, message);
    }

    public byte[] configuration(String name) {
        return bytecode(name, "java/lang/Object", Opcodes.ACC_PUBLIC, Type.getDescriptor(Configuration.class), false, "configuration");
    }

    public byte[] brokenSuperclass(String name, boolean bean) {
        return bytecode(name, "unavailable/MissingParent", Opcodes.ACC_PUBLIC,
                bean ? Type.getDescriptor(Bean.class) : null, false, "broken");
    }

    public byte[] initializationProbe(String name) {
        return bytecode(name, "java/lang/Object", Opcodes.ACC_PUBLIC, Type.getDescriptor(Bean.class), true, "probe");
    }

    public byte[] abstractBean(String name) {
        return bytecode(name, "java/lang/Object", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                Type.getDescriptor(Bean.class), false, "abstract");
    }

    public byte[] beanInterface(String name) {
        return bytecode(name, "java/lang/Object", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                Type.getDescriptor(Bean.class), false, "interface");
    }

    public String entryName(String name) {
        return name.replace('.', '/') + ".class";
    }

    public Path jar(Path path, Map<String, byte[]> entries, boolean multiRelease) throws IOException {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (multiRelease) {
            manifest.getMainAttributes().putValue("Multi-Release", "true");
        }
        try (var output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }

    private byte[] bytecode(String name, String parent, int access, String annotation, boolean failInitialization, String message) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, access | (accessHasInterface(access) ? 0 : Opcodes.ACC_SUPER), name.replace('.', '/'), null, parent, null);
        if (annotation != null) {
            writer.visitAnnotation(annotation, true).visitEnd();
        }
        if (!accessHasInterface(access)) {
            var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(1, 1);
            constructor.visitEnd();

            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "message", "()Ljava/lang/String;", null, null);
            method.visitCode();
            method.visitLdcInsn(message);
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        }
        if (failInitialization) {
            var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            initializer.visitCode();
            initializer.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
            initializer.visitInsn(Opcodes.DUP);
            initializer.visitLdcInsn("Scanner initialized probe");
            initializer.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
            initializer.visitInsn(Opcodes.ATHROW);
            initializer.visitMaxs(3, 0);
            initializer.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private boolean accessHasInterface(int access) {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

}
