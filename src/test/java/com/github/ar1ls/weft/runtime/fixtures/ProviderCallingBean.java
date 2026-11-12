package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProviderCallingBean {

    SimpleBean dependency;

    public ProviderCallingBean(Provider<SimpleBean> provider) {
        this.dependency = provider.get();
    }

}
