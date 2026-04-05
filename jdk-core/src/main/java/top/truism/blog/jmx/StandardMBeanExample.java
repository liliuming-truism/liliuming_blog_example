package top.truism.blog.jmx;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * Standard MBean: the most common MBean type.
 *
 * Lifecycle:
 *   1. Define a <ClassName>MBean interface (attributes + operations)
 *   2. Implement it in <ClassName>
 *   3. Register with MBeanServer under an ObjectName
 *   4. Tools (JConsole, VisualVM) or code read/write attributes and invoke operations
 *
 * ObjectName format: "domain:key=value[,key=value,...]"
 *   Canonical example: "com.example:type=CacheManager,name=primary"
 */
public class StandardMBeanExample {

    public static void main(String[] args) throws Exception {
        // The platform MBeanServer is shared across the JVM
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        ObjectName name = new ObjectName("top.truism.blog:type=CacheManager,name=primary");

        CacheManager cache = new CacheManager(100);
        mbs.registerMBean(cache, name);

        // --- Drive some activity ---
        cache.put("user:1", "Alice");
        cache.put("user:2", "Bob");
        cache.get("user:1");    // hit
        cache.get("user:999");  // miss

        // --- Read attributes through MBeanServer (same API a remote client uses) ---
        System.out.println("=== Standard MBean ===");
        System.out.println("size      : " + mbs.getAttribute(name, "Size"));
        System.out.println("hitCount  : " + mbs.getAttribute(name, "HitCount"));
        System.out.println("missCount : " + mbs.getAttribute(name, "MissCount"));
        System.out.printf ("hitRate   : %.2f%n", mbs.getAttribute(name, "HitRate"));

        // --- Invoke an operation through MBeanServer ---
        // signature array must match the method's parameter types
        mbs.invoke(name, "evict",
                new Object[]{"user:2"},
                new String[]{String.class.getName()});
        System.out.println("size after evict: " + mbs.getAttribute(name, "Size"));

        // --- Write an attribute ---
        mbs.setAttribute(name, new javax.management.Attribute("MaxSize", 200));
        System.out.println("maxSize after set: " + mbs.getAttribute(name, "MaxSize"));

        // --- Introspect the MBean's metadata ---
        MBeanInfo info = mbs.getMBeanInfo(name);
        System.out.println("\nMBean class : " + info.getClassName());
        System.out.println("Attributes  : " + info.getAttributes().length);
        System.out.println("Operations  : " + info.getOperations().length);
        for (var attr : info.getAttributes()) {
            System.out.printf("  [attr] %-12s  type=%-10s  r=%b w=%b%n",
                    attr.getName(), attr.getType(), attr.isReadable(), attr.isWritable());
        }

        mbs.unregisterMBean(name);
    }
}
