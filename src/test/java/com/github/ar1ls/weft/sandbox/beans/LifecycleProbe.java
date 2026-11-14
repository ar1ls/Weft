package com.github.ar1ls.weft.sandbox.beans;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.api.annotations.PreDestroy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Bean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LifecycleProbe implements AutoCloseable {

    @Inject
    MessageFormatter formatter;

    @Getter
    String message;

    public LifecycleProbe() {
        System.out.println("Lifecycle: constructor, formatter injected: " + (this.formatter != null));
    }

    @PostConstruct
    private void initialize() {
        System.out.println("Lifecycle: PostConstruct, formatter injected: " + (this.formatter != null));
        this.message = this.formatter.format("Ready");
    }

    @PreDestroy
    private void destroy() {
        System.out.println("Lifecycle: PreDestroy, message: " + this.message);
    }

    @Override
    public void close() {
        System.out.println("Lifecycle: AutoCloseable.close");
    }

}
