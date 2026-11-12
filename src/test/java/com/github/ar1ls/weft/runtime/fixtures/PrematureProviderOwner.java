package com.github.ar1ls.weft.runtime.fixtures;

import com.github.ar1ls.weft.api.Provider;

public class PrematureProviderOwner {

    public PrematureProviderOwner(Provider<PrematureProviderTarget> provider) {
        provider.get();
    }

}
