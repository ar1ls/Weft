package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.Instance;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class InstanceFieldBean implements AutoCloseable {

    @Instance
    @Getter
    @Setter
    private static volatile InstanceFieldBean instance;

    @Instance
    @Getter
    private static volatile AutoCloseable contract;

    @Getter
    private static volatile InstanceFieldBean unannotated;

    @Inject
    SimpleBean dependency;

    @Getter
    AutoCloseable observed;

    @Getter
    SimpleBean observedDependency;

    @Setter
    RuntimeException failure;

    public static AutoCloseable fromFactory() {
        return new InstanceFieldBean();
    }

    @PostConstruct
    private void initialize() {
        this.observed = contract;
        this.observedDependency = this.dependency;
        if (this.failure != null) {
            throw this.failure;
        }
    }

    @Override
    public void close() {
    }

}
