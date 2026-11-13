package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.Subscription;
import com.github.ar1ls.weft.api.lifecycle.BeanReadyEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReadySubscription implements Subscription {

    final Consumer<BeanReadyEvent> listener;
    final ArrayDeque<BeanReadyEvent> pending = new ArrayDeque<>();
    boolean closed;
    boolean draining;

    public synchronized void enqueue(BeanReadyEvent event) {
        if (!this.closed) {
            this.pending.add(event);
        }
    }

    public void drain() {
        synchronized (this) {
            if (this.closed || this.draining) {
                return;
            }
            this.draining = true;
        }
        while (true) {
            BeanReadyEvent event;
            synchronized (this) {
                event = this.closed ? null : this.pending.poll();
                if (event == null) {
                    this.draining = false;
                    return;
                }
            }
            try {
                this.listener.accept(event);
            } catch (Throwable failure) {
                log.error("Bean ready listener failed for {}", event.definition().key(), failure);
            }
        }
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.pending.clear();
    }

}
