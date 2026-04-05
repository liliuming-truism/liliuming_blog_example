package top.truism.blog.jmx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standard MBean implementation.
 * When registered with an MBeanServer, attributes and operations defined in
 * CacheManagerMBean become visible/invokable via JConsole, VisualVM, or
 * any MBeanServerConnection.
 */
public class CacheManager implements CacheManagerMBean {

    // LinkedHashMap preserves insertion order for simple LRU-style eviction
    private final Map<String, String> store = new LinkedHashMap<>();
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private volatile int maxSize;

    public CacheManager(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public int getSize() {
        return store.size();
    }

    @Override
    public long getHitCount() {
        return hitCount.get();
    }

    @Override
    public long getMissCount() {
        return missCount.get();
    }

    @Override
    public double getHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void put(String key, String value) {
        if (store.size() >= maxSize) {
            String oldest = store.keySet().iterator().next();
            store.remove(oldest);
        }
        store.put(key, value);
    }

    @Override
    public String get(String key) {
        String val = store.get(key);
        if (val != null) hitCount.incrementAndGet();
        else missCount.incrementAndGet();
        return val;
    }

    @Override
    public void evict(String key) {
        store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }
}
