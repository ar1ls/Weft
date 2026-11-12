package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.api.annotations.PreDestroy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShutdownResource implements AutoCloseable {

    final String name;

    @Getter
    final List<String> events;

    @Setter
    RuntimeException initializationFailure;

    @Setter
    RuntimeException destructionFailure;

    @Setter
    Error closeFailure;

    @Setter
    Runnable beforeDestroy = () -> {};

    @PostConstruct
    private void initialize() {
        if (this.initializationFailure != null) {
            throw this.initializationFailure;
        }
    }

    @PreDestroy
    private void destroy() {
        this.events.add(this.name + ":destroy");
        this.beforeDestroy.run();
        if (this.destructionFailure != null) {
            throw this.destructionFailure;
        }
    }

    @Override
    public void close() {
        this.events.add(this.name + ":close");
        if (this.closeFailure != null) {
            throw this.closeFailure;
        }
    }

}
