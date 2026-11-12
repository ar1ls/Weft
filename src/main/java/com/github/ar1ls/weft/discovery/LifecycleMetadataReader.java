package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class LifecycleMetadataReader {

    public List<Method> readPostConstruct(@NonNull Class<?> type) {
        var hierarchy = InjectionMetadataReader.hierarchy(type);
        var hooks = new ArrayList<Method>();
        for (var index = 0; index < hierarchy.size(); index++) {
            Method hook = null;
            for (var method : hierarchy.get(index).getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic() || !method.isAnnotationPresent(PostConstruct.class)) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                        || method.getParameterCount() != 0 || method.getReturnType() != void.class) {
                    throw new DefinitionException("PostConstruct must be a concrete nonstatic void method without arguments: "
                            + method.toGenericString());
                }
                if (hook != null) {
                    throw new DefinitionException("Multiple PostConstruct methods in: " + hierarchy.get(index).getName());
                }
                hook = method;
            }
            if (hook == null || isOverridden(hook, hierarchy.subList(index + 1, hierarchy.size()))) {
                continue;
            }
            if (!hook.trySetAccessible()) {
                throw new DefinitionException("Cannot access PostConstruct method: " + hook.toGenericString());
            }
            hooks.add(hook);
        }
        return List.copyOf(hooks);
    }

    private boolean isOverridden(Method hook, List<Class<?>> descendants) {
        var modifiers = hook.getModifiers();
        if (Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers)) {
            return false;
        }
        for (var descendant : descendants) {
            for (var method : descendant.getDeclaredMethods()) {
                if (!method.getName().equals(hook.getName()) || method.getParameterCount() != 0
                        || Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                var owner = hook.getDeclaringClass();
                if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)
                        || (owner.getPackageName().equals(descendant.getPackageName())
                        && owner.getClassLoader() == descendant.getClassLoader())) {
                    return true;
                }
            }
        }
        return false;
    }

}
