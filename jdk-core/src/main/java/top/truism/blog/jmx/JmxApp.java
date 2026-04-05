package top.truism.blog.jmx;

/**
 * JMX (Java Management Extensions) examples entry point.
 *
 * Topics covered:
 *   1. StandardMBeanExample  — register a Standard MBean; read/write attributes and invoke operations
 *   2. PlatformMXBeanExample — read JVM metrics from built-in platform MXBeans (memory, threads, GC)
 *   3. NotificationExample   — emit and receive JMX notifications (push model)
 *   4. MXBeanExample         — MXBean open-data types (CompositeData) for remote-safe attribute exchange
 *   5. JmxRemoteExample      — expose MBeanServer over RMI; programmatic remote client connection
 *
 * Run each example individually via its own main() method to isolate output.
 */
public class JmxApp {

    public static void main(String[] args) throws Exception {
        StandardMBeanExample.main(args);
        System.out.println();

        PlatformMXBeanExample.main(args);
        System.out.println();

        NotificationExample.main(args);
        System.out.println();

        MXBeanExample.main(args);
        System.out.println();

        JmxRemoteExample.main(args);
    }
}
