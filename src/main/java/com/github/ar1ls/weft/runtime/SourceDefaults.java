package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.Definitions;
import com.github.ar1ls.weft.api.SourceDefault;
import com.github.ar1ls.weft.api.SourceDefaultFactory;
import com.github.ar1ls.weft.api.exception.DefinitionException;
import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class SourceDefaults {

    private static final String SOURCE_PREFIX = "weft-source-default:";

    public <T> BeanDefinition instance(String sourceId, Class<T> type, T instance) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(type, "type");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("Consumer source id must not be blank");
        }
        var id = SOURCE_PREFIX + sourceId.length() + ":" + sourceId + ":" + type.getName();
        var supplied = Definitions.instance("instance", type, instance, false);
        var source = new BeanSource(id, supplied.source().classLoader());
        return new BeanDefinition(new BeanKey(source.id(), "instance"), supplied.type(), sourceId,
                supplied.scope(), false, false, source, supplied.factory());
    }

    public List<SourceDefault> expand(BeanRegistry registry, Collection<BeanSource> sources,
                                     List<SourceDefault> explicit, List<SourceDefaultFactory<?>> factories) {
        var configured = new LinkedHashMap<String, Set<TypeRef<?>>>();
        for (var binding : explicit) {
            if (!configured.computeIfAbsent(binding.sourceId(), key -> new HashSet<>()).add(binding.type())) {
                throw new DefinitionException("Duplicate source default for " + binding.type() + " in source " + binding.sourceId());
            }
        }
        var types = new HashSet<Class<?>>();
        for (var factory : factories) {
            if (!types.add(factory.type())) {
                throw new DefinitionException("Duplicate source defaults factory for " + factory.type().getName());
            }
        }
        var bindings = new ArrayList<>(explicit);
        for (var source : sources) {
            if (source.id().startsWith(SOURCE_PREFIX)) {
                continue;
            }
            for (var factory : factories) {
                var type = TypeRef.of(factory.type());
                if (configured.getOrDefault(source.id(), Set.of()).contains(type)) {
                    continue;
                }
                var definition = create(factory, source);
                registry.register(definition);
                bindings.add(new SourceDefault(source.id(), type, definition.key()));
            }
        }
        return List.copyOf(bindings);
    }

    private <T> BeanDefinition create(SourceDefaultFactory<T> factory, BeanSource source) {
        try {
            return instance(source.id(), factory.type(), factory.factory().apply(source));
        } catch (RuntimeException e) {
            throw new DefinitionException("Source defaults factory failed for " + factory.type().getName()
                    + " in source " + source.id(), e);
        }
    }

}
