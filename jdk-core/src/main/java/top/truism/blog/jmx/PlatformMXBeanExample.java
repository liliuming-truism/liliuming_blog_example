package top.truism.blog.jmx;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Platform MXBeans: pre-registered MBeans that expose JVM internals.
 * Accessible without registration via ManagementFactory static methods.
 *
 * Key platform MXBeans:
 *   RuntimeMXBean    - JVM name, uptime, classpath, system properties
 *   MemoryMXBean     - heap / non-heap usage
 *   ThreadMXBean     - live/daemon/peak thread counts, deadlock detection
 *   ClassLoadingMXBean - loaded/unloaded class counts
 *   GarbageCollectorMXBean - GC name, collection count and time
 *   MemoryPoolMXBean - per-pool memory usage (Eden, Survivor, Old Gen, …)
 *   OperatingSystemMXBean - OS name, available processors, system load
 */
public class PlatformMXBeanExample {

    public static void main(String[] args) {
        System.out.println("=== Platform MXBeans ===");

        // Runtime
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        System.out.println("JVM name     : " + runtime.getVmName());
        System.out.println("JVM vendor   : " + runtime.getVmVendor());
        System.out.println("JVM version  : " + runtime.getVmVersion());
        System.out.println("Uptime (ms)  : " + runtime.getUptime());

        System.out.println();

        // Memory
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap    = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        System.out.printf("Heap     used=%dMB  committed=%dMB  max=%dMB%n",
                mb(heap.getUsed()), mb(heap.getCommitted()), mb(heap.getMax()));
        System.out.printf("Non-heap used=%dMB  committed=%dMB%n",
                mb(nonHeap.getUsed()), mb(nonHeap.getCommitted()));

        System.out.println();

        // Threads
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        System.out.println("Thread count : " + threads.getThreadCount());
        System.out.println("Daemon count : " + threads.getDaemonThreadCount());
        System.out.println("Peak threads : " + threads.getPeakThreadCount());
        long[] deadlocked = threads.findDeadlockedThreads();
        System.out.println("Deadlocked   : " + (deadlocked == null ? 0 : deadlocked.length));

        System.out.println();

        // Garbage collectors
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.printf("GC [%-22s] count=%3d  time=%dms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }

        System.out.println();

        // Memory pools (individual regions like Eden, Survivor, Old Gen)
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage u = pool.getUsage();
            System.out.printf("Pool [%-32s] %-8s  used=%4dKB%n",
                    pool.getName(), pool.getType(), u.getUsed() / 1024);
        }
    }

    private static long mb(long bytes) {
        return bytes / 1024 / 1024;
    }
}
