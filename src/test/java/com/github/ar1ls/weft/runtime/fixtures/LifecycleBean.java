package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class LifecycleBean extends LifecycleBaseBean {

    @Inject
    @Getter
    Provider<SimpleBean> provider;

    @Getter
    SimpleBean observed;

    @PostConstruct
    private void initialize() {
        this.observed = this.getSimple();
        this.getEvents().add("child");
    }

    public static LifecycleBean fromFactory() {
        return new LifecycleBean();
    }

}
