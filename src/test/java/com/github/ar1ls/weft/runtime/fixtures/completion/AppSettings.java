package com.github.ar1ls.weft.runtime.fixtures.completion;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.api.annotations.Properties;
import com.github.ar1ls.weft.api.annotations.Property;

import java.util.List;

@Bean
@Properties("app")
public record AppSettings(Address address, @Property(defaultValue = "1,2,3") List<Integer> levels) {
}
