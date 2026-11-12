package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InjectedConstructorBean {

    SimpleBean dependency;

    public InjectedConstructorBean() {
        this.dependency = null;
    }

    @Inject
    public InjectedConstructorBean(SimpleBean dependency) {
        this.dependency = dependency;
    }

}
