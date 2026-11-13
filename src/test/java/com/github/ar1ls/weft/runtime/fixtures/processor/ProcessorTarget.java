package com.github.ar1ls.weft.runtime.fixtures.processor;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.api.annotations.PreDestroy;
import com.github.ar1ls.weft.api.annotations.Property;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcessorTarget implements AutoCloseable {

    final List<String> events;

    @Property(value = "app.text", defaultValue = "ready")
    String text;

    @Inject
    SimpleBean dependency;

    @Setter
    boolean failClose;

    @Inject
    public ProcessorTarget() {
        this(new ArrayList<>());
    }

    public static ProcessorTarget fromFactory() {
        return new ProcessorTarget();
    }

    public String message() {
        return this.text;
    }

    @PostConstruct
    private void initialize() {
        this.events.add("init:" + this.text + ":" + (this.dependency != null));
    }

    @PreDestroy
    private void destroy() {
        this.events.add("destroy");
    }

    @Override
    public void close() {
        this.events.add("close");
        if (this.failClose) {
            throw new IllegalStateException("close failed");
        }
    }

}
