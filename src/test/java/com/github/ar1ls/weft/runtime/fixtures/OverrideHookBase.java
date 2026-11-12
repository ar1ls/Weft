package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.annotations.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OverrideHookBase {

    @Getter
    List<String> events = new ArrayList<>();

    @PostConstruct
    protected void initialize() {
        this.events.add("base");
    }

}
