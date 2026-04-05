package top.truism.blog.jdk9.varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.UnaryOperator;

public class AtomicReference<V> {
    private volatile V value;
    private static final VarHandle VALUE_HANDLE;

    static {
        try {
            VALUE_HANDLE = MethodHandles.lookup()
                .findVarHandle(AtomicReference.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public AtomicReference(V initialValue) {
        this.value = initialValue;
    }

    public AtomicReference() {
        this(null);
    }

    public V get() {
        return (V) VALUE_HANDLE.getVolatile(this);
    }

    public void set(V newValue) {
        VALUE_HANDLE.setVolatile(this, newValue);
    }

    public boolean compareAndSet(V expected, V update) {
        return VALUE_HANDLE.compareAndSet(this, expected, update);
    }

    public V compareAndExchange(V expected, V update) {
        return (V) VALUE_HANDLE.compareAndExchange(this, expected, update);
    }

    public V getAndSet(V newValue) {
        return (V) VALUE_HANDLE.getAndSet(this, newValue);
    }

    public V getAndUpdate(UnaryOperator<V> updateFunction) {
        V prev = get(), next = null;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = updateFunction.apply(prev);
            if (compareAndSet(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    public V updateAndGet(UnaryOperator<V> updateFunction) {
        V prev = get(), next = null;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = updateFunction.apply(prev);
            if (compareAndSet(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }
}

