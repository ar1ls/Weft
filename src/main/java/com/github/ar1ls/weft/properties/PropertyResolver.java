package com.github.ar1ls.weft.properties;

import com.github.ar1ls.weft.spi.PropertySource;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PropertyResolver implements PropertySource {

    Map<String, String> explicit;
    Map<String, String> system;
    Map<String, String> environment;

    public PropertyResolver(Map<String, String> explicit) {
        this(explicit, snapshotSystemProperties(), System.getenv());
    }

    public PropertyResolver(Map<String, String> explicit, Map<String, String> system, Map<String, String> environment) {
        this.explicit = Map.copyOf(explicit);
        this.system = Map.copyOf(system);
        this.environment = Map.copyOf(environment);
    }

    @Override
    public Optional<String> find(@NonNull String key) {
        if (this.explicit.containsKey(key)) {
            return Optional.of(this.explicit.get(key));
        }
        if (this.system.containsKey(key)) {
            return Optional.of(this.system.get(key));
        }
        return Optional.ofNullable(this.environment.get(key));
    }

    private static Map<String, String> snapshotSystemProperties() {
        var snapshot = new LinkedHashMap<String, String>();
        var properties = System.getProperties();
        synchronized (properties) {
            for (var key : properties.stringPropertyNames()) {
                snapshot.put(key, properties.getProperty(key));
            }
        }
        return snapshot;
    }

}
