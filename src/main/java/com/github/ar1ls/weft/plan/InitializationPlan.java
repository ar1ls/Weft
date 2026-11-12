package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.definition.BeanKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InitializationPlan(Map<BeanKey, CreationPlan> beans, List<BeanKey> creationOrder) {

    public InitializationPlan {
        beans = Collections.unmodifiableMap(new LinkedHashMap<>(beans));
        creationOrder = List.copyOf(creationOrder);
    }

}
