package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@UtilityClass
public class FallbackSelector {

    public List<BeanDefinition> select(@NonNull Collection<BeanDefinition> definitions) {
        var ordinary = new ArrayList<BeanDefinition>();
        var fallbacks = new ArrayList<BeanDefinition>();
        for (var definition : definitions) {
            if (definition.missingTypes().isEmpty()) {
                ordinary.add(definition);
            } else {
                validate(definition);
                fallbacks.add(definition);
            }
        }
        var active = new ArrayList<BeanDefinition>();
        for (var fallback : fallbacks) {
            if (!hasCandidate(fallback, ordinary)) {
                active.add(fallback);
            }
        }
        active.sort(Comparator.comparing((BeanDefinition definition) -> definition.key().sourceId())
                .thenComparing(definition -> definition.key().localId()));
        for (var index = 0; index < active.size(); index++) {
            var first = active.get(index);
            for (var other = index + 1; other < active.size(); other++) {
                var second = active.get(other);
                if (hasCandidate(first, List.of(second)) || hasCandidate(second, List.of(first))) {
                    throw new DefinitionException("Conflicting fallback definitions: " + first.key() + " and " + second.key());
                }
            }
        }
        ordinary.addAll(active);
        return List.copyOf(ordinary);
    }

    private boolean hasCandidate(BeanDefinition fallback, List<BeanDefinition> candidates) {
        for (var contract : fallback.missingTypes()) {
            for (var candidate : candidates) {
                if (!(candidate.type().type() instanceof Class<?> provided)) {
                    throw new DefinitionException("Only class types are supported: " + candidate.key());
                }
                if (contract.isAssignableFrom(provided)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validate(BeanDefinition fallback) {
        if (!(fallback.type().type() instanceof Class<?> declaredType) || declaredType.isPrimitive()) {
            throw new DefinitionException("Fallback requires a reference Class type: " + fallback.key());
        }
        for (var contract : fallback.missingTypes()) {
            if (!contract.isAssignableFrom(declaredType)) {
                throw new DefinitionException("Fallback type must implement " + contract.getName() + ": " + fallback.key());
            }
        }
    }

}
