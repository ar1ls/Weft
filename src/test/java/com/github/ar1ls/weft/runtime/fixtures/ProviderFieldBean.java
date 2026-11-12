package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.annotations.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderFieldBean {

    @Inject
    @Getter
    Provider<SimpleBean> provider;

}
