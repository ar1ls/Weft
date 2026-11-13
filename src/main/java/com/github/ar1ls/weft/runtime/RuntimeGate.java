package com.github.ar1ls.weft.runtime;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class RuntimeGate {

    final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    RuntimeState state = RuntimeState.NEW;
    int active;
    Thread closing;

    public synchronized void start() {
        if (this.state != RuntimeState.NEW) {
            throw new IllegalStateException("Cannot start runtime in state: " + this.state);
        }
        this.state = RuntimeState.STARTING;
        this.active++;
        this.depth.set(1);
    }

    public synchronized void started(boolean success) {
        this.state = success ? RuntimeState.RUNNING : RuntimeState.FAILED;
        this.exit();
    }

    public synchronized void enter() {
        var nested = this.depth.get() > 0;
        if (this.state != RuntimeState.RUNNING && !(nested && (this.state == RuntimeState.STARTING || this.state == RuntimeState.CLOSING))) {
            throw new IllegalStateException("Cannot access runtime in state: " + this.state);
        }
        this.active++;
        this.depth.set(this.depth.get() + 1);
    }

    public synchronized void exit() {
        var remaining = this.depth.get() - 1;
        if (remaining == 0) {
            this.depth.remove();
        } else {
            this.depth.set(remaining);
        }
        this.active--;
        this.notifyAll();
    }

    public synchronized boolean beginClose() {
        if (this.depth.get() > 0) {
            throw new IllegalStateException("Cannot close runtime from an active creation/callback");
        }
        if (this.state == RuntimeState.STARTING) {
            throw new IllegalStateException("Cannot close runtime during startup");
        }
        if (this.closing == Thread.currentThread()) {
            return false;
        }
        var interrupted = false;
        try {
            while (this.state == RuntimeState.CLOSING) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (this.state == RuntimeState.CLOSED) {
                return false;
            }
            this.state = RuntimeState.CLOSING;
            this.closing = Thread.currentThread();
            while (this.active != 0) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void closed() {
        this.state = RuntimeState.CLOSED;
        this.closing = null;
        this.notifyAll();
    }

    public synchronized boolean isClosed() {
        return this.state == RuntimeState.CLOSED;
    }

}
