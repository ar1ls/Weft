package com.github.ar1ls.weft;

import com.github.ar1ls.weft.api.WeftBuilder;
import com.github.ar1ls.weft.api.WeftOptions;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Weft {

    public WeftBuilder builder() {
        return new WeftBuilder();
    }

    public void initialization(WeftOptions options) {
        throw new UnsupportedOperationException("Weft runtime is not implemented yet");
    }

    public void initialization() {
        initialization(builder().build());
    }

}
