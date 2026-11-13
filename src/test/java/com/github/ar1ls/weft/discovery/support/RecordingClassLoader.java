package com.github.ar1ls.weft.discovery.support;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecordingClassLoader extends ClassLoader {

    Map<String, byte[]> classes;
    List<String> requested = new ArrayList<>();

    public RecordingClassLoader(Map<String, byte[]> classes) {
        super(RecordingClassLoader.class.getClassLoader());
        this.classes = Map.copyOf(classes);
    }

    public List<String> requestedClasses() {
        return List.copyOf(this.requested);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        this.requested.add(name);
        var bytecode = this.classes.get(name);
        if (bytecode == null) {
            throw new ClassNotFoundException(name);
        }
        return this.defineClass(name, bytecode, 0, bytecode.length);
    }

}
