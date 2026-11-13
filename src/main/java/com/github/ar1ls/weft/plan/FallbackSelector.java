package com.github.ar1ls.weft.plan;

import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.Types;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class FallbackSelector {

    public List<BeanDefinition> select(@NonNull Collection<BeanDefinition> definitions) {
        var groups = new ArrayList<BeanDefinition>();
        var external = new ArrayList<BeanDefinition>();
        for (var definition : definitions) {
            if (isGroup(definition)) {
                groups.add(definition);
            } else if (definition.conditionOwner() == null && definition.missingTypes().isEmpty()) {
                external.add(definition);
            }
        }
        var groupKeys = new HashSet<BeanKey>();
        for (var group : groups) {
            if (group.missingTypes().isEmpty()) {
                throw new DefinitionException("Conditional configuration requires missing contracts: " + group.key());
            }
            groupKeys.add(group.key());
        }
        for (var definition : definitions) {
            if (definition.conditionOwner() != null && !groupKeys.contains(definition.conditionOwner())) {
                throw new DefinitionException("Unknown conditional configuration owner: " + definition.conditionOwner() + " for bean: " + definition.key());
            }
        }
        var enabled = new HashSet<BeanKey>();
        groups.sort(Comparator.comparing((BeanDefinition definition) -> definition.key().sourceId()).thenComparing(definition -> definition.key().localId()));
        for (var group : groups) {
            if (!hasCandidate(group, external)) {
                enabled.add(group.key());
            }
        }
        for (var group : groups) {
            if (!enabled.contains(group.key())) {
                continue;
            }
            var otherProducts = new ArrayList<BeanDefinition>();
            for (var definition : definitions) {
                if (definition.conditionOwner() != null && !definition.conditionOwner().equals(group.key())
                        && enabled.contains(definition.conditionOwner())) {
                    otherProducts.add(definition);
                }
            }
            otherProducts.sort(Comparator.comparing((BeanDefinition definition) -> definition.key().sourceId()).thenComparing(definition -> definition.key().localId()));
            for (var product : otherProducts) {
                if (hasCandidate(group, List.of(product))) {
                    throw new DefinitionException("Conflicting configuration fallbacks: " + group.key() + " and " + product.conditionOwner());
                }
            }
        }
        var ordinary = new ArrayList<BeanDefinition>();
        var fallbacks = new ArrayList<BeanDefinition>();
        for (var definition : definitions) {
            if (definition.conditionOwner() != null && !enabled.contains(definition.conditionOwner())) {
                continue;
            }
            if (isGroup(definition) && !enabled.contains(definition.key())) {
                continue;
            }
            if (definition.missingTypes().isEmpty() || isGroup(definition)) {
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
                var provided = Types.raw(candidate.type().type());
                if (contract.isAssignableFrom(provided)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validate(BeanDefinition fallback) {
        var declaredType = Types.raw(fallback.type().type());
        for (var contract : fallback.missingTypes()) {
            if (!contract.isAssignableFrom(declaredType)) {
                throw new DefinitionException("Fallback type must implement " + contract.getName() + ": " + fallback.key());
            }
        }
    }

    private boolean isGroup(BeanDefinition definition) {
        return definition.key().equals(definition.conditionOwner());
    }

}
