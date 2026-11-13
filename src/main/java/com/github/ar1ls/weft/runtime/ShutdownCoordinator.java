package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.exception.ShutdownException;
import com.github.ar1ls.weft.definition.BeanKey;
import com.github.ar1ls.weft.definition.Types;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShutdownCoordinator {

    Map<BeanKey, ManagedBean> beans = new LinkedHashMap<>();
    Map<BeanKey, Set<BeanKey>> dependencies = new LinkedHashMap<>();
    Set<Object> destroyed = Collections.newSetFromMap(new IdentityHashMap<>());

    public synchronized void track(ManagedBean bean) {
        for (var managed : this.beans.values()) {
            if (managed.original() == bean.original()) {
                var existing = managed.plan().definition().key();
                var alias = bean.plan().definition().key();
                this.recordDependency(existing, alias);
                this.recordDependency(alias, existing);
            }
        }
        this.beans.put(bean.plan().definition().key(), bean);
    }

    public synchronized void recordDependency(BeanKey consumer, BeanKey dependency) {
        if (!consumer.equals(dependency)) {
            this.dependencies.computeIfAbsent(consumer, key -> new LinkedHashSet<>()).add(dependency);
        }
    }

    public void close() {
        var failure = new ShutdownException("Cannot shut down all managed beans");
        var reachable = new LinkedHashMap<BeanKey, Set<BeanKey>>();
        for (var key : this.beans.keySet()) {
            var targets = new HashSet<BeanKey>();
            this.collectDependencies(key, targets);
            reachable.put(key, targets);
        }
        var remaining = new ArrayList<>(this.beans.keySet());
        try {
            while (!remaining.isEmpty()) {
                var group = this.nextGroup(remaining, reachable);
                for (var key : group) {
                    var error = this.destroy(this.beans.get(key));
                    if (error != null) {
                        failure.addSuppressed(error);
                    }
                    remaining.remove(key);
                }
            }
        } finally {
            this.beans.clear();
            this.dependencies.clear();
            this.destroyed.clear();
        }
        if (failure.getSuppressed().length != 0) {
            throw failure;
        }
    }

    public ShutdownException destroyFailed(ManagedBean bean) {
        for (var managed : this.snapshot()) {
            if (managed.original() == bean.original()) {
                return null;
            }
        }
        try {
            return this.destroy(bean);
        } finally {
            synchronized (this) {
                this.destroyed.remove(bean.original());
            }
        }
    }

    private ShutdownException destroy(ManagedBean bean) {
        synchronized (this) {
            if (!this.destroyed.add(bean.original())) {
                return null;
            }
        }
        for (var managed : this.snapshot()) {
            var type = Types.raw(bean.plan().definition().type().type());
            var candidate = Types.raw(managed.plan().definition().type().type());
            if (managed.original() == bean.original() && type.isAssignableFrom(candidate)) {
                bean = managed;
            }
        }
        var failure = new ShutdownException("Cannot destroy bean: " + bean.plan().definition().key());
        var closeInvoked = false;
        for (var hook : bean.plan().preDestroy()) {
            closeInvoked |= this.isCloseMethod(bean.original(), hook);
            try {
                hook.invoke(bean.original());
            } catch (InvocationTargetException e) {
                failure.addSuppressed(new ShutdownException("PreDestroy failed at " + hook.toGenericString(), e.getCause()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                failure.addSuppressed(new ShutdownException("Cannot invoke PreDestroy " + hook.toGenericString(), e));
            }
        }
        if (!closeInvoked && bean.original() instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception | Error e) {
                failure.addSuppressed(new ShutdownException("AutoCloseable failed for bean: " + bean.plan().definition().key(), e));
            }
        }
        return failure.getSuppressed().length == 0 ? null : failure;
    }

    private void collectDependencies(BeanKey key, Set<BeanKey> targets) {
        for (var target : this.dependencies.getOrDefault(key, Set.of())) {
            if (targets.add(target)) {
                this.collectDependencies(target, targets);
            }
        }
    }

    private synchronized List<ManagedBean> snapshot() {
        return new ArrayList<>(this.beans.values());
    }

    private List<BeanKey> nextGroup(List<BeanKey> remaining, Map<BeanKey, Set<BeanKey>> reachable) {
        for (var candidate : remaining.reversed()) {
            var blocked = false;
            for (var consumer : remaining) {
                if (reachable.get(consumer).contains(candidate) && !reachable.get(candidate).contains(consumer)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                continue;
            }
            var group = new ArrayList<BeanKey>();
            for (var key : remaining.reversed()) {
                if (key.equals(candidate) || (reachable.get(candidate).contains(key) && reachable.get(key).contains(candidate))) {
                    group.add(key);
                }
            }
            return group;
        }
        throw new IllegalStateException("Cannot order managed beans for shutdown");
    }

    private boolean isCloseMethod(Object bean, Method hook) {
        if (!(bean instanceof AutoCloseable) || !hook.getName().equals("close") || Modifier.isPrivate(hook.getModifiers())) {
            return false;
        }
        if (Modifier.isPublic(hook.getModifiers()) || Modifier.isProtected(hook.getModifiers())) {
            return true;
        }
        try {
            var owner = hook.getDeclaringClass();
            var implementation = bean.getClass().getMethod("close").getDeclaringClass();
            return owner.getPackageName().equals(implementation.getPackageName())
                    && owner.getClassLoader() == implementation.getClassLoader();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

}
