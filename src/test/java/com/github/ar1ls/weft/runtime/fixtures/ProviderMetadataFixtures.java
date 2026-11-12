package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;

import java.util.List;

public class ProviderMetadataFixtures {

    public void raw(Provider provider) {
    }

    public void wildcard(Provider<?> provider) {
    }

    public void nested(Provider<List<SimpleBean>> provider) {
    }

    public <T> void variable(Provider<T> provider) {
    }

}
