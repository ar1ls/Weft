package com.github.ar1ls.weft.runtime.fixtures.propertiesadvanced;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;
import com.github.ar1ls.weft.api.annotations.Scope;
import com.github.ar1ls.weft.api.BeanScope;

import java.util.Map;
import java.util.Set;

@Bean
@Properties("advanced")
@Scope(BeanScope.PROTOTYPE)
public record AdvancedSettings(MutableSettings endpoint, GenericSetting<Integer> limit,
        @Property(defaultValue = "a=1,b=2") Map<String, Integer> weights,
        @Property(defaultValue = "one,one,two") Set<String> labels) {
}
