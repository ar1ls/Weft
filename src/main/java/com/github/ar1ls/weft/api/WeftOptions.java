package com.github.ar1ls.weft.api;

import com.github.ar1ls.weft.definition.BeanDefinition;
import com.github.ar1ls.weft.discovery.ScanSource;
import com.github.ar1ls.weft.spi.BeanProcessor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public record WeftOptions(
        List<BeanDefinition> definitions,
        List<ScanSource> sources,
        Map<String, String> properties,
        List<BeanProcessor> processors
) {
    public WeftOptions {
        definitions = List.copyOf(definitions);
        sources = List.copyOf(sources);
        properties = Map.copyOf(properties);
        processors = List.copyOf(processors);
    }

    @Override
    public @NonNull String toString() {
        return "WeftOptions[definitions=" + this.definitions.size()
                + ", sources=" + this.sources.size()
                + ", propertyCount=" + this.properties.size()
                + ", processors=" + this.processors.size() + "]";
    }
}
