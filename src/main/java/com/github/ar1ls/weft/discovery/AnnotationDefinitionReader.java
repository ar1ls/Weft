package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Lazy;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.Primary;
import com.github.ar1ls.weft.api.annotations.Scope;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.spi.BeanFactory;
import lombok.NonNull;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AnnotationDefinitionReader {

    public List<BeanDefinition> read(@NonNull Class<?> type, @NonNull BeanSource source) {
        var configuration = type.isAnnotationPresent(Configuration.class);
        if (!configuration && !type.isAnnotationPresent(Bean.class)) {
            return List.of();
        }

        var definitions = new ArrayList<BeanDefinition>();
        var key = new BeanKey(source.id(), type.getName());
        definitions.add(this.definition(key, TypeRef.of(type), type, source, new ConstructorBeanFactory(type)));

        this.validateMethods(type, configuration);
        if (configuration) {
            var methods = type.getMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
            for (var method : methods) {
                if (!method.isAnnotationPresent(Bean.class) || method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                var methodKey = new BeanKey(source.id(), type.getName() + "#" + method.toGenericString());
                var factory = new MethodBeanFactory(method, type);
                definitions.add(this.definition(methodKey, TypeRef.of(method.getGenericReturnType()), method, source, factory));
            }
        }
        return List.copyOf(definitions);
    }

    private void validateMethods(Class<?> type, boolean configuration) {
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var method : current.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class) || method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (!configuration || !Modifier.isPublic(method.getModifiers())) {
                    throw new DefinitionException("Public @Bean methods require @Configuration: " + method.toGenericString());
                }
            }
        }
    }

    private BeanDefinition definition(BeanKey key, TypeRef<?> type, AnnotatedElement element, BeanSource source, BeanFactory factory) {
        var scope = element.getAnnotation(Scope.class);
        var named = element.getAnnotation(Named.class);
        var name = named == null ? "" : named.value();
        return new BeanDefinition(key, type, name, scope == null ? BeanScope.SINGLETON : scope.value(),
                element.isAnnotationPresent(Lazy.class), element.isAnnotationPresent(Primary.class), source, factory);
    }

}
