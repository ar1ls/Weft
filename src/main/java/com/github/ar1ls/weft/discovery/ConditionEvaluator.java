
package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.condition.Condition;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

@UtilityClass
public class ConditionEvaluator {

    public boolean matches(@NonNull AnnotatedElement element, @NonNull PropertySource properties) {
        return evaluate(element, properties).matches();
    }

    public Evaluation evaluate(@NonNull AnnotatedElement element, @NonNull PropertySource properties) {
        var property = element.getAnnotation(ConditionalOnProperty.class);
        var env = element.getAnnotation(ConditionalOnEnv.class);

        if (property != null) {
            validate(property.name(), element);
        }
        if (env != null) {
            validate(env.name(), element);
        }

        var method = element instanceof Class<?> type ? conditionMethod(type) : null;

        if (property != null && !matches(properties, property.prefix().strip(), property.name(),
                property.havingValue(), property.matchIfMissing())) {
            return new Evaluation(false, "@ConditionalOnProperty" + Arrays.toString(property.name()));
        }

        if (env != null && !matches(properties, "", env.name(),
                env.havingValue(), env.matchIfMissing())) {
            return new Evaluation(false, "@ConditionalOnEnv" + Arrays.toString(env.name()));
        }

        if (method != null) {
            return invoke(method);
        }

        return new Evaluation(true, "");
    }

    private Method conditionMethod(Class<?> type) {
        Method condition = null;

        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            var methods = current.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));

            for (var method : methods) {
                if (!method.isAnnotationPresent(Condition.class) || method.isBridge() || method.isSynthetic()) {
                    continue;
                }

                if (condition != null) {
                    throw new DefinitionException("Only one @Condition method is allowed in " + type.getName()
                            + ": " + condition.toGenericString() + ", " + method.toGenericString());
                }

                if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0 || method.getReturnType() != boolean.class) {
                    throw new DefinitionException("@Condition method must be public static boolean with no arguments: "
                            + method.toGenericString());
                }

                condition = method;
            }
        }

        return condition;
    }

    private Evaluation invoke(Method method) {
        try {
            if (!method.trySetAccessible()) {
                throw new DefinitionException("Cannot access @Condition method: " + method.toGenericString());
            }

            if ((boolean) method.invoke(null)) {
                return new Evaluation(true, "");
            }

            return new Evaluation(false, "@Condition " + method.toGenericString() + " returned false");

        } catch (IllegalAccessException e) {
            throw new DefinitionException("Cannot access @Condition method: " + method.toGenericString(), e);
        } catch (InvocationTargetException e) {
            throw new DefinitionException("@Condition method failed: " + method.toGenericString(), e.getCause());
        }
    }

    private boolean matches(PropertySource properties, String prefix, String[] names,
                            String expected, boolean matchIfMissing) {
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

    public record Evaluation(boolean matches, String reason) {
    }

}
