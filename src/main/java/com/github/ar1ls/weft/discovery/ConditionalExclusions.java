package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConditionalExclusions {

    PropertySource properties;
    Map<BeanKey, ExcludedBean> excluded = new LinkedHashMap<>();

    public ConditionalExclusions(PropertySource properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void inspect(Class<?> type, BeanSource source) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");

        var configuration = type.isAnnotationPresent(Configuration.class);
        if (!configuration && !type.isAnnotationPresent(Bean.class)) {
            return;
        }

        var ownerMatches = ConditionEvaluator.matches(type, this.properties);
        if (!ownerMatches) {
            this.add(new BeanKey(source.id(), type.getName()), TypeRef.of(type),
                    name(type), describe(type));
        }
        if (!configuration) {
            return;
        }

        for (Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(Bean.class) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            if (ownerMatches && ConditionEvaluator.matches(method, this.properties)) {
                continue;
            }
            var typeRef = TypeRef.of(Types.member(type, method.getDeclaringClass(), method.getGenericReturnType()));
            var key = new BeanKey(source.id(), type.getName() + "#" + method.toGenericString());
            this.add(key, typeRef, name(method), ownerMatches ? describe(method) : "owner " + describe(type));
        }
    }

    public List<ExcludedBean> entries() {
        return List.copyOf(this.excluded.values());
    }

    private void add(BeanKey key, TypeRef<?> type, String name, String reason) {
        this.excluded.putIfAbsent(key, new ExcludedBean(key, type, name, reason));
    }

    private static String name(AnnotatedElement element) {
        var named = element.getAnnotation(Named.class);
        return named == null ? "" : named.value().strip();
    }

    private static String describe(AnnotatedElement element) {
        var parts = new ArrayList<String>();
        var env = element.getAnnotation(ConditionalOnEnv.class);
        var property = element.getAnnotation(ConditionalOnProperty.class);
        if (env != null) {
            parts.add("@ConditionalOnEnv" + Arrays.toString(env.name()));
        }
        if (property != null) {
            parts.add("@ConditionalOnProperty" + Arrays.toString(property.name()));
        }
        return String.join(" and ", parts);
    }

    public record ExcludedBean(BeanKey key, TypeRef<?> type, String name, String reason) {
        public ExcludedBean {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reason, "reason");
            name = name == null ? "" : name.strip();
        }
    }
    
}
