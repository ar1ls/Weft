package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnnotatedCloseResource implements AutoCloseable {

    List<String> events;

    @Override
    @PreDestroy
    public void close() {
        this.events.add("annotated:close");
    }

}
