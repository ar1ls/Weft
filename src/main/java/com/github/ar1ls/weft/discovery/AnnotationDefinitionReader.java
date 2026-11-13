package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.annotations.*;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.spi.BeanFactory;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnnotationDefinitionReader {

    PropertySource properties;

    public AnnotationDefinitionReader() {
        this(new PropertyResolver(Map.of()));
    }

    public AnnotationDefinitionReader(@NonNull PropertySource properties) {
        this.properties = properties;
    }

    public List<BeanDefinition> read(@NonNull Class<?> type, @NonNull BeanSource source) {
        var configuration = type.isAnnotationPresent(Configuration.class);
        if (!configuration && !type.isAnnotationPresent(Bean.class)) {
            return List.of();
        }

        if (!ConditionEvaluator.matches(type, this.properties)) {
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
                if (!ConditionEvaluator.matches(method, this.properties)) {
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
        if (named != null && named.value().isBlank()) {
            throw new DefinitionException("Bean name must not be blank: " + key);
        }
        var name = named == null ? "" : named.value();
        return new BeanDefinition(key, type, name, scope == null ? BeanScope.SINGLETON : scope.value(),
                element.isAnnotationPresent(Lazy.class), element.isAnnotationPresent(Primary.class), source, factory);
    }

}
