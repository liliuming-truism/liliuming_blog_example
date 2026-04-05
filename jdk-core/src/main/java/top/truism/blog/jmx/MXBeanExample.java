package top.truism.blog.jmx;

import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import java.lang.management.ManagementFactory;

/**
 * MXBean: a variant of Standard MBean that converts complex return types to
 * "open data" types (CompositeData, TabularData, simple primitives).
 *
 * Why MXBean over Standard MBean?
 *   Standard MBeans expose custom Java types — a remote client must have those
 *   classes on its classpath to deserialise the value.
 *   MXBeans convert to open types automatically, so any client can read the data
 *   without sharing application classes.
 *
 * How to declare an MXBean:
 *   Option A — name the interface <ClassName>MXBean  (naming convention)
 *   Option B — annotate the interface with @MXBean   (shown here)
 *
 * Type mapping examples:
 *   Custom class  → CompositeData  (fields become composite keys)
 *   List<T>       → TabularData or array of CompositeData
 *   Enum          → String
 *   Primitives, String, BigDecimal, Date — passed through unchanged
 */
public class MXBeanExample {

    // A plain Java class — NOT shared with the remote client.
    // MXBean converts it to CompositeData automatically.
    public static class ConnectionStats {
        private final int active;
        private final int idle;
        private final int total;

        public ConnectionStats(int active, int idle) {
            this.active = active;
            this.idle   = idle;
            this.total  = active + idle;
        }

        public int getActive() { return active; }
        public int getIdle()   { return idle;   }
        public int getTotal()  { return total;  }
    }

    @MXBean
    public interface ConnectionPoolMXBean {
        ConnectionStats getStats();     // → CompositeData over the wire
        int getMinSize();
        int getMaxSize();
        void setMaxSize(int maxSize);
    }

    public static class ConnectionPool implements ConnectionPoolMXBean {
        private final int minSize;
        private volatile int maxSize;
        private volatile int activeCount = 3; // simulated

        public ConnectionPool(int minSize, int maxSize) {
            this.minSize  = minSize;
            this.maxSize  = maxSize;
        }

        @Override public ConnectionStats getStats()            { return new ConnectionStats(activeCount, maxSize - activeCount); }
        @Override public int  getMinSize()                     { return minSize; }
        @Override public int  getMaxSize()                     { return maxSize; }
        @Override public void setMaxSize(int maxSize)          { this.maxSize = maxSize; }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("top.truism.blog:type=ConnectionPool");

        ConnectionPool pool = new ConnectionPool(2, 10);
        mbs.registerMBean(pool, name);

        System.out.println("=== MXBean (Open Data Types) ===");
        System.out.println("maxSize  : " + mbs.getAttribute(name, "MaxSize"));

        // The client receives CompositeData — no ConnectionStats class needed
        Object raw = mbs.getAttribute(name, "Stats");
        System.out.println("raw type : " + raw.getClass().getSimpleName()); // CompositeDataSupport

        CompositeData stats = (CompositeData) raw;
        System.out.println("active   : " + stats.get("active"));
        System.out.println("idle     : " + stats.get("idle"));
        System.out.println("total    : " + stats.get("total"));

        // The CompositeType describes the fields — equivalent to the class schema
        System.out.println("keys     : " + stats.getCompositeType().keySet());

        mbs.unregisterMBean(name);
    }
}
