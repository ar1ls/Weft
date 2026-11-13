package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.AnnotatedElement;

@UtilityClass
public class ConditionEvaluator {

    public boolean matches(@NonNull AnnotatedElement element, @NonNull PropertySource properties) {
        if (element.isAnnotationPresent(ConditionalOnMissingBean.class)) {
            throw new DefinitionException("@ConditionalOnMissingBean is not supported yet: " + element);
        }
        var property = element.getAnnotation(ConditionalOnProperty.class);
        var env = element.getAnnotation(ConditionalOnEnv.class);
        if (property != null) {
            validate(property.name(), element);
        }
        if (env != null) {
            validate(env.name(), element);
        }
        var propertyMatches = property == null || matches(properties, property.prefix().strip(), property.name(),
                property.havingValue(), property.matchIfMissing());
        var envMatches = env == null || matches(properties, "", env.name(), env.havingValue(), env.matchIfMissing());
        return propertyMatches && envMatches;
    }

    private boolean matches(PropertySource properties, String prefix, String[] names, String expected, boolean matchIfMissing) {
        for (var name : names) {
            var key = prefix.isEmpty() ? name.strip() : prefix + "." + name.strip();
            var value = properties.find(key);
            if (value.isEmpty()) {
                if (!matchIfMissing) {
                    return false;
                }
            } else if (expected.isEmpty() ? value.get().equalsIgnoreCase("false") : !value.get().equals(expected)) {
                return false;
            }
        }
        return true;
    }

    private void validate(String[] names, AnnotatedElement element) {
        if (names.length == 0) {
            throw new DefinitionException("Condition must declare at least one key: " + element);
        }
        for (var name : names) {
            if (name.isBlank()) {
                throw new DefinitionException("Condition key must not be blank: " + element);
            }
        }
    }

}
