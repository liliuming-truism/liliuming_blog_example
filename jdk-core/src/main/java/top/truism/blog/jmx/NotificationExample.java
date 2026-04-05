package top.truism.blog.jmx;

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMX Notifications: MBeans push events to registered listeners.
 *
 * Sender side:
 *   - Extend NotificationBroadcasterSupport (or implement NotificationBroadcaster)
 *   - Call sendNotification(Notification) to dispatch
 *   - Override getNotificationInfo() to advertise event types (used by tools like JConsole)
 *
 * Receiver side:
 *   - Register a NotificationListener via MBeanServer.addNotificationListener()
 *   - Optionally pass a NotificationFilter to receive only matching events
 *   - The handback object is passed back to the listener with every notification
 *
 * Notification.type follows a dot-separated convention: "threshold.exceeded"
 */
public class NotificationExample {

    // --- MBean interface ---
    public interface ThresholdGuardMBean {
        int getThreshold();
        void setThreshold(int threshold);
        // Records a value; emits a notification if value > threshold
        void record(int value);
    }

    // --- MBean implementation ---
    public static class ThresholdGuard
            extends NotificationBroadcasterSupport implements ThresholdGuardMBean {

        static final String TYPE_EXCEEDED = "threshold.exceeded";

        private volatile int threshold;
        private final AtomicLong seq = new AtomicLong();

        public ThresholdGuard(int threshold) {
            this.threshold = threshold;
        }

        @Override public int  getThreshold()           { return threshold; }
        @Override public void setThreshold(int t)      { this.threshold = t; }

        @Override
        public void record(int value) {
            if (value > threshold) {
                Notification n = new Notification(
                        TYPE_EXCEEDED,
                        this,                           // source
                        seq.incrementAndGet(),          // sequence number (monotonic)
                        System.currentTimeMillis(),
                        "Value " + value + " exceeded threshold " + threshold
                );
                n.setUserData(value);   // attach arbitrary payload
                sendNotification(n);
            }
        }

        // Advertise what notification types this MBean may emit.
        // JConsole and other management tools use this to show subscription options.
        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[]{
                    new MBeanNotificationInfo(
                            new String[]{TYPE_EXCEEDED},
                            Notification.class.getName(),
                            "Emitted when a recorded value exceeds the configured threshold"
                    )
            };
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("top.truism.blog:type=ThresholdGuard");

        ThresholdGuard guard = new ThresholdGuard(50);
        mbs.registerMBean(guard, name);

        // Expect exactly 2 notifications (75 and 99 exceed the threshold of 50)
        CountDownLatch latch = new CountDownLatch(2);

        mbs.addNotificationListener(name,
                (notification, handback) -> {
                    System.out.printf("  [Listener] type=%-20s  seq=%d  msg='%s'  payload=%s%n",
                            notification.getType(),
                            notification.getSequenceNumber(),
                            notification.getMessage(),
                            notification.getUserData());
                    latch.countDown();
                },
                null,   // filter: null = receive all
                "ctx"   // handback: passed back to listener on every call
        );

        System.out.println("=== JMX Notifications (threshold=50) ===");
        guard.record(30);  // below → no notification
        guard.record(75);  // above → notification
        guard.record(42);  // below → no notification
        guard.record(99);  // above → notification

        latch.await();     // wait for both listener callbacks to complete
        mbs.unregisterMBean(name);
    }
}
