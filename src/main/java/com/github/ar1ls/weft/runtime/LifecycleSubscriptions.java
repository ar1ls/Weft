package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.Subscription;
import com.github.ar1ls.weft.api.lifecycle.BeanReadyEvent;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class LifecycleSubscriptions {

    List<BeanReadyEvent> ready = new ArrayList<>();
    List<ReadySubscription> listeners = new ArrayList<>();

    public Subscription subscribe(Consumer<BeanReadyEvent> listener) {
        var subscription = new ReadySubscription(Objects.requireNonNull(listener));
        synchronized (this) {
            for (var event : this.ready) {
                subscription.enqueue(event);
            }
            this.listeners.add(subscription);
        }
        subscription.drain();
        return () -> {
            synchronized (this) {
                this.listeners.remove(subscription);
            }
            subscription.close();
        };
    }

    public void publish(BeanReadyEvent event) {
        List<ReadySubscription> listeners;
        synchronized (this) {
            this.ready.add(event);
            listeners = List.copyOf(this.listeners);
            for (var listener : listeners) {
                listener.enqueue(event);
            }
        }
        for (var listener : listeners) {
            listener.drain();
        }
    }

    public synchronized void clear() {
        for (var listener : this.listeners) {
            listener.close();
        }
        this.listeners.clear();
        this.ready.clear();
    }

}
