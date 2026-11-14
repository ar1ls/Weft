package com.github.ar1ls.weft.api;

import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.spi.BeanProcessor;
import com.github.ar1ls.weft.spi.DefinitionContributor;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

public record WeftOptions(
        List<BeanDefinition> definitions,
        List<ScanSource> sources,
        Map<String, String> properties,
        List<BeanProcessor> processors,
        List<DefinitionContributor> contributors,
        List<SourceDefault> sourceDefaults,
        List<SourceDefaultFactory<?>> sourceDefaultFactories
) {

    public WeftOptions(List<BeanDefinition> definitions, List<ScanSource> sources, Map<String, String> properties,
                       List<BeanProcessor> processors) {
        this(definitions, sources, properties, processors, List.of(), List.of(), List.of());
    }

    public WeftOptions(List<BeanDefinition> definitions, List<ScanSource> sources, Map<String, String> properties,
                       List<BeanProcessor> processors, List<DefinitionContributor> contributors) {
        this(definitions, sources, properties, processors, contributors, List.of(), List.of());
    }

    public WeftOptions(List<BeanDefinition> definitions, List<ScanSource> sources, Map<String, String> properties,
                       List<BeanProcessor> processors, List<DefinitionContributor> contributors, List<SourceDefault> sourceDefaults) {
        this(definitions, sources, properties, processors, contributors, sourceDefaults, List.of());
    }

    public WeftOptions {
        definitions = List.copyOf(definitions);
        sources = List.copyOf(sources);
        properties = Map.copyOf(properties);
        processors = List.copyOf(processors);
        contributors = List.copyOf(contributors);
        sourceDefaults = List.copyOf(sourceDefaults);
        sourceDefaultFactories = List.copyOf(sourceDefaultFactories);
    }

    @Override
    public @NonNull String toString() {
        return "WeftOptions[definitions=" + this.definitions.size()
                + ", sources=" + this.sources.size()
                + ", propertyCount=" + this.properties.size()
                + ", processors=" + this.processors.size()
                + ", contributors=" + this.contributors.size()
                + ", sourceDefaults=" + this.sourceDefaults.size()
                + ", sourceDefaultFactories=" + this.sourceDefaultFactories.size() + "]";
    }

}
