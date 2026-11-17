
package com.github.ar1ls.weft.discovery;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Configuration;
import com.github.ar1ls.weft.api.annotations.Named;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.definition.Types;
import com.github.ar1ls.weft.spi.PropertySource;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConditionalExclusions {

    @NonNull
    PropertySource properties;
    Map<BeanKey, ExcludedBean> excluded = new LinkedHashMap<>();

    public ConditionalExclusions(@NonNull PropertySource properties) {
        this.properties = properties;
    }

    public ConditionEvaluator.Evaluation inspect(@NonNull Class<?> type, @NonNull BeanSource source) {
        var configuration = type.isAnnotationPresent(Configuration.class);
        if (!configuration && !type.isAnnotationPresent(Bean.class)) {
            return new ConditionEvaluator.Evaluation(true, "");
        }
        var owner = ConditionEvaluator.evaluate(type, this.properties);
        if (!owner.matches()) {
            this.add(
                    new BeanKey(source.id(), type.getName()),
                    TypeRef.of(type),
                    name(type),
                    owner.reason()
            );
        }

        if (!configuration) {
            return owner;
        }

        for (Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(Bean.class) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            var condition = owner.matches()
                    ? ConditionEvaluator.evaluate(method, this.properties)
                    : owner;
            if (condition.matches()) {
                continue;
            }
            var typeRef = TypeRef.of(Types.member(
                    type,
                    method.getDeclaringClass(),
                    method.getGenericReturnType()
            ));
            var key = new BeanKey(
                    source.id(),
                    type.getName() + "#" + method.toGenericString()
            );
            this.add(key, typeRef, name(method), condition.reason());
        }
        return owner;
    }

    public List<ExcludedBean> entries() {
        return List.copyOf(this.excluded.values());
    }

    private void add(BeanKey key, TypeRef<?> type, String name, String reason) {
        if (this.excluded.putIfAbsent(
                key,
                new ExcludedBean(key, type, name, reason)
        ) == null) {
            log.warn("SKIPPED bean {}: {}", key, reason);
        }
    }

    private static String name(AnnotatedElement element) {
        var named = element.getAnnotation(Named.class);
        return named == null ? "" : named.value().strip();
    }

    public record ExcludedBean(
            BeanKey key,
            TypeRef<?> type,
            String name,
            String reason
    ) {
        public ExcludedBean {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reason, "reason");
            name = name == null ? "" : name.strip();
        }
    }

}
