package com.github.ar1ls.weft;

import com.github.ar1ls.weft.api.Subscription;
import com.github.ar1ls.weft.api.WeftBuilder;
import com.github.ar1ls.weft.api.WeftOptions;
import com.github.ar1ls.weft.api.lifecycle.BeanReadyEvent;
import com.github.ar1ls.weft.definition.TypeRef;
import com.github.ar1ls.weft.runtime.WeftBootstrap;
import com.github.ar1ls.weft.runtime.WeftRuntime;
import lombok.experimental.UtilityClass;

import java.util.function.Consumer;

@UtilityClass
public class Weft {

    private static final Object MONITOR = new Object();
    private volatile WeftRuntime runtime;
    private boolean starting;
    private boolean stopped;

    public WeftBuilder builder() {
        return new WeftBuilder();
    }

    public void initialization(WeftOptions options) {
        synchronized (MONITOR) {
            if (starting || runtime != null || stopped) {
                throw new IllegalStateException("Global Weft already started or closed");
            }
            starting = true;
        }
        try {
            var created = WeftBootstrap.start(options);
            synchronized (MONITOR) {
                runtime = created;
            }
        } finally {
            synchronized (MONITOR) {
                starting = false;
            }
        }
    }

    public <T> T get(Class<T> type) {
        return current().get(type);
    }

    public <T> T get(Class<T> type, String name) {
        return current().get(type, name);
    }

    public <T> T get(TypeRef<T> type) {
        return current().get(type);
    }

    public <T> T get(TypeRef<T> type, String name) {
        return current().get(type, name);
    }

    public <T> T create(TypeRef<T> type) {
        return current().create(type);
    }

    public <T> T create(Class<T> type) {
        return current().create(type);
    }

    public <T> T create(TypeRef<T> type, String consumerSourceId) {
        return current().create(type, consumerSourceId);
    }

    public <T> T create(Class<T> type, String consumerSourceId) {
        return current().create(type, consumerSourceId);
    }

    public void inject(Object instance) {
        current().inject(instance);
    }

    public void inject(Object instance, String consumerSourceId) {
        current().inject(instance, consumerSourceId);
    }

    public Subscription subscribe(Consumer<BeanReadyEvent> listener) {
        return current().subscribe(listener);
    }

    public void close() {
        WeftRuntime closing;
        synchronized (MONITOR) {
            if (starting) {
                throw new IllegalStateException("Global Weft is starting");
            }
            closing = runtime;
            if (closing == null) {
                stopped = true;
            }
        }
        if (closing != null) {
            try {
                closing.close();
            } finally {
                synchronized (MONITOR) {
                    if (closing.isClosed()) {
                        runtime = null;
                        stopped = true;
                    }
                }
            }
        }
    }

    private WeftRuntime current() {
        var current = runtime;
        if (current == null) {
            throw new IllegalStateException("Global Weft is not running");
        }
        return current;
    }

    public void initialization() {
        initialization(builder().build());
    }

}
