package com.github.ar1ls.weft.api;

import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.BeanSource;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.runtime.SourceDefaults;
import com.github.ar1ls.weft.runtime.WeftBootstrap;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import com.github.ar1ls.weft.spi.BeanProcessor;
import com.github.ar1ls.weft.spi.DefinitionContributor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.*;
import java.util.function.Function;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WeftBuilder {

    List<BeanDefinition> definitions = new ArrayList<>();
    List<ScanSource> sources = new ArrayList<>();
    Map<String, String> properties = new LinkedHashMap<>();
    List<BeanProcessor> processors = new ArrayList<>();
    List<DefinitionContributor> contributors = new ArrayList<>();
    List<SourceDefault> sourceDefaults = new ArrayList<>();
    List<SourceDefaultFactory<?>> sourceDefaultFactories = new ArrayList<>();

    public <T> WeftBuilder sourceDefaults(Class<T> type, Function<BeanSource, ? extends T> factory) {
        this.sourceDefaultFactories.add(new SourceDefaultFactory<>(type, factory));
        return this;
    }

    public <T> WeftBuilder sourceDefault(String sourceId, Class<T> type, T instance) {
        var definition = SourceDefaults.instance(sourceId, type, instance);
        var binding = new SourceDefault(sourceId, TypeRef.of(type), definition.key());
        this.definitions.add(definition);
        this.sourceDefaults.add(binding);
        return this;
    }

    public WeftBuilder sourceDefault(String sourceId, TypeRef<?> type, BeanKey target) {
        this.sourceDefaults.add(new SourceDefault(sourceId, type, target));
        return this;
    }

    public WeftBuilder contributor(DefinitionContributor contributor) {
        this.contributors.add(Objects.requireNonNull(contributor, "contributor"));
        return this;
    }

    public WeftRuntime start() {
        return WeftBootstrap.start(this.build());
    }

    public WeftBuilder register(BeanDefinition definition) {
        this.definitions.add(Objects.requireNonNull(definition, "definition"));
        return this;
    }

    public WeftBuilder register(Class<?> type) {
        return this.register(Definitions.of(type));
    }

    public WeftBuilder register(Class<?> type, String sourceId) {
        return this.register(Definitions.of(type, sourceId));
    }

    public WeftBuilder scan(ScanSource source) {
        this.sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    public WeftBuilder property(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Property key must not be blank");
        }
        this.properties.put(key, value);
        return this;
    }

    public WeftBuilder processor(BeanProcessor processor) {
        this.processors.add(Objects.requireNonNull(processor, "processor"));
        return this;
    }

    public WeftOptions build() {
        return new WeftOptions(this.definitions, this.sources, this.properties, this.processors, this.contributors,
                this.sourceDefaults, this.sourceDefaultFactories);
    }

}
