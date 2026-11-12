package com.github.ar1ls.weft.runtime.fixtures.properties;

import com.github.ar1ls.weft.api.annotations.Env;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.api.annotations.Property;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LifecycleSettings {

    @Property(value = "app.text", defaultValue = "default")
    String text;

    @Env(value = "APP_LIMIT", defaultValue = "4")
    int limit;

    @Env(value = "APP_OPTIONAL", required = false)
    String retained = "initial";

    @Env("APP_REQUIRED")
    String required;

    String observed;

    @PostConstruct
    private void initialize() {
        this.observed = this.text + ":" + this.limit;
    }

    public static LifecycleSettings fromFactory() {
        return new LifecycleSettings();
    }

}
