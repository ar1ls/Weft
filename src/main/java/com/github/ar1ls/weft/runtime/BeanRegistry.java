package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BeanRegistry {

    final Map<BeanKey, BeanDefinition> definitions = new LinkedHashMap<>();

    @Getter
    boolean frozen;

    public void register(BeanDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (this.frozen) {
            throw new IllegalStateException("Bean registry is frozen");
        }
        if (this.definitions.putIfAbsent(definition.key(), definition) != null) {
            throw new DefinitionException("Duplicate bean key: " + definition.key());
        }
    }

    public Map<BeanKey, BeanDefinition> freeze() {
        this.frozen = true;
        return this.definitions();
    }

    public Map<BeanKey, BeanDefinition> definitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.definitions));
    }

}
