package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

@UtilityClass
public class ClassMetadataReader {

    private static final String BEAN = Type.getDescriptor(Bean.class);
    private static final String CONFIGURATION = Type.getDescriptor(Configuration.class);

    public boolean isCandidate(@NonNull InputStream bytecode, @NonNull String className) throws IOException {
        var metadata = new ClassNode();
        new ClassReader(bytecode).accept(metadata, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!className.equals(metadata.name.replace('/', '.'))) {
            throw new IllegalArgumentException("Class name does not match entry: " + className);
        }
        if ((metadata.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC)) != 0
                || metadata.visibleAnnotations == null) {
            return false;
        }
        for (var annotation : metadata.visibleAnnotations) {
            if (annotation.desc.equals(BEAN) || annotation.desc.equals(CONFIGURATION)) {
                return true;
            }
        }
        return false;
    }

}
