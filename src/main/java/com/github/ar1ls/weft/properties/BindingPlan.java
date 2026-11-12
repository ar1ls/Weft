package com.github.ar1ls.weft.properties;

import java.util.List;

public record BindingPlan(List<PropertyBinding> fields) {

    public BindingPlan {
        fields = List.copyOf(fields);
    }

}
