package top.truism.blog.jmx;

/**
 * Standard MBean interface.
 * Naming convention: implementation class name + "MBean" suffix.
 * JMX derives attributes from getters/setters and operations from other public methods.
 */
public interface CacheManagerMBean {

    // --- Attributes (readable via getter, writable via setter) ---

    int getSize();

    long getHitCount();

    long getMissCount();

    double getHitRate();

    int getMaxSize();

    void setMaxSize(int maxSize);

    // --- Operations ---

    void put(String key, String value);

    String get(String key);

    void evict(String key);

    void clear();
}
