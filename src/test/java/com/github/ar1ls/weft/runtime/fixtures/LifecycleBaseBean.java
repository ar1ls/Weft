package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class LifecycleBaseBean {

    @Inject
    @Getter
    SimpleBean simple = new SimpleBean();

    @Getter
    final List<String> events = new ArrayList<>(List.of("constructor"));

    @Getter
    SimpleBean untouched = new SimpleBean();

    @PostConstruct
    private void initialize() {
        this.events.add("base");
    }

}
