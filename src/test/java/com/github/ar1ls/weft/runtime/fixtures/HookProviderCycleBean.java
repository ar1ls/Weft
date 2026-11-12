package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class HookProviderCycleBean {

    @Inject
    Provider<HookProviderCycleBean> self;

    @PostConstruct
    private void initialize() {
        this.self.get();
    }

}
