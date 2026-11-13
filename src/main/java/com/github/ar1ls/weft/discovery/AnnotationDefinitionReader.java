package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.BeanScope;
import com.github.ar1ls.weft.api.annotations.*;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.properties.PropertyResolver;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.factory.MethodBeanFactory;
import com.github.ar1ls.weft.spi.BeanFactory;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class AnnotationDefinitionReader {

    @NonNull
    PropertySource properties;

    public AnnotationDefinitionReader() {
        this(new PropertyResolver(Map.of()));
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
        var owner = this.definition(key, TypeRef.of(type), type, source, new ConstructorBeanFactory(type));
        var conditionalConfiguration = configuration && !owner.missingTypes().isEmpty();
        definitions.add(new BeanDefinition(owner.key(), owner.type(), owner.name(), owner.scope(), owner.lazy(),
                owner.primary(), owner.source(), owner.factory(), owner.missingTypes(), conditionalConfiguration ? key : null));

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
                var factory = new MethodBeanFactory(method, type, key);
                var product = this.definition(methodKey, TypeRef.of(Types.member(type, method.getDeclaringClass(), method.getGenericReturnType())), method, source, factory);
                definitions.add(new BeanDefinition(product.key(), product.type(), product.name(), product.scope(), product.lazy(),
                        product.primary(), product.source(), product.factory(), product.missingTypes(),
                        conditionalConfiguration ? key : null));
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
        List<Class<?>> missingTypes = getMissingTypes(key, type, element);
        return new BeanDefinition(key, type, name, scope == null ? BeanScope.SINGLETON : scope.value(),
                element.isAnnotationPresent(Lazy.class), element.isAnnotationPresent(Primary.class), source, factory, missingTypes);
    }

    private static @NonNull List<Class<?>> getMissingTypes(BeanKey key, TypeRef<?> type, AnnotatedElement element) {
        var missing = element.getAnnotation(ConditionalOnMissingBean.class);
        List<Class<?>> missingTypes = List.of();
        if (missing != null) {
            var declaredType = Types.raw(type.type());
            var configuration = element.isAnnotationPresent(Configuration.class);
            if (configuration && missing.value().length == 0) {
                throw new DefinitionException("@ConditionalOnMissingBean on @Configuration requires explicit contracts; use @Bean methods for inferred types: " + key);
            }
            missingTypes = missing.value().length == 0 ? List.of(declaredType) : List.of(missing.value());
            for (var contract : missingTypes) {
                if (contract.isPrimitive() || (!configuration && !contract.isAssignableFrom(declaredType))) {
                    throw new DefinitionException("Fallback type must implement " + contract.getName() + ": " + key);
                }
            }
        }
        return missingTypes;
    }

}
