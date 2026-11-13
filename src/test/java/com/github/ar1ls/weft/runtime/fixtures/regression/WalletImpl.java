package com.github.ar1ls.weft.runtime.fixtures.regression;

import com.github.ar1ls.weft.api.annotations.Inject;
import com.github.ar1ls.weft.api.annotations.PostConstruct;
import com.github.ar1ls.weft.api.annotations.PreDestroy;
import com.github.ar1ls.weft.api.annotations.Property;
import com.github.ar1ls.weft.runtime.fixtures.SimpleBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletImpl implements Wallet, AutoCloseable {

    final LifecycleTrace trace;

    @Inject
    @Getter
    SimpleBean dependency;

    @Property(value = "wallet.status", defaultValue = "ready")
    @Getter(onMethod_ = @Override)
    @Accessors(fluent = true)
    String status;

    @PostConstruct
    private void initialize() {
        this.trace.events().add("init:" + (this.dependency != null) + ":" + this.status);
    }

    @PreDestroy
    private void destroy() {
        this.trace.events().add("destroy");
    }

    @Override
    public void close() {
        this.trace.events().add("close");
    }

}
