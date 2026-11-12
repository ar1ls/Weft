package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;

public record ProviderDependentBean(Provider<SimpleBean> provider) {
}
