package top.truism.blog.jdk9.varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class VarHandleAccessModeExample {

    // 测试用的字段
    private int data = 0;


    private boolean ready = false;
    private long counter = 0;

    // VarHandle实例
    private static final VarHandle DATA_HANDLE;
    private static final VarHandle READY_HANDLE;
    private static final VarHandle COUNTER_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            DATA_HANDLE = lookup.findVarHandle(VarHandleAccessModeExample.class, "data", int.class);
            READY_HANDLE = lookup.findVarHandle(VarHandleAccessModeExample.class, "ready", boolean.class);
            COUNTER_HANDLE = lookup.findVarHandle(VarHandleAccessModeExample.class, "counter", long.class);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        VarHandleAccessModeExample demo = new VarHandleAccessModeExample();

        System.out.println("=== VarHandle 访问模式效果对比 ===\n");

        // 1. Plain模式 - 可能出现可见性问题
        demo.testPlainMode();

        // 2. Opaque模式 - 原子性但无排序保证
        demo.testOpaqueMode();

        // 3. Acquire/Release模式 - 建立happens-before关系
        demo.testAcquireReleaseMode();

        // 4. Volatile模式 - 完整的内存屏障
        demo.testVolatileMode();

        // 5. 性能对比
        demo.performanceComparison();

        // 6. 内存重排序演示
        demo.demonstrateMemoryReordering();
    }

    /**
     * 1. Plain模式测试 - 展示可见性问题
     */
    public void testPlainMode() throws InterruptedException {
        System.out.println("1. Plain模式测试 - 可能的可见性问题");

        // 重置状态
        DATA_HANDLE.set(this, 0);
        READY_HANDLE.set(this, false);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger visibilityIssues = new AtomicInteger(0);

        // 写线程
        Thread writer = new Thread(() -> {
            try {
                latch.await();
                // 使用Plain模式写入
                DATA_HANDLE.set(this, 42);
                READY_HANDLE.set(this, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 读线程
        Thread reader = new Thread(() -> {
            try {
                latch.await();
                Thread.sleep(10  ); // 给写线程一点时间

                // 使用Plain模式读取
                boolean isReady = (boolean) READY_HANDLE.get(this);
                int dataValue = (int) DATA_HANDLE.get(this);

                if (isReady && dataValue == 0) {
                    visibilityIssues.incrementAndGet();
                    System.out.println("  发现可见性问题: ready=true 但 data=0");
                } else {
                    System.out.println("  读取结果: ready=" + isReady + ", data=" + dataValue);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        writer.start();
        reader.start();
        latch.countDown();

        writer.join();
        reader.join();

        System.out.println("  Plain模式可见性问题次数: " + visibilityIssues.get());
        System.out.println();
    }

    /**
     * 2. Opaque模式测试 - 原子性保证
     */
    public void testOpaqueMode() throws InterruptedException {
        System.out.println("2. Opaque模式测试 - 原子性保证，无排序保证");

        COUNTER_HANDLE.set(this, 0);
        int numThreads = 4;
        int incrementsPerThread = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        // 创建多个线程进行原子递增
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < incrementsPerThread; j++) {
                        // 使用Opaque模式进行原子递增
                        long current, updated;
                        do {
                            current = (long) COUNTER_HANDLE.getOpaque(this);
                            updated = current + 1;
                        }
                        while (!COUNTER_HANDLE.compareAndSet(this, current, updated));
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        endLatch.await();

        long finalValue = (long) COUNTER_HANDLE.getOpaque(this);
        long expectedValue = (long) numThreads * incrementsPerThread;

        System.out.println("  期望值: " + expectedValue);
        System.out.println("  实际值: " + finalValue);
        System.out.println("  原子性保证: " + (finalValue == expectedValue ? "✓" : "✗"));
        System.out.println();
    }

    /**
     * 3. Acquire/Release模式测试 - happens-before关系
     */
    public void testAcquireReleaseMode() throws InterruptedException {
        System.out.println("3. Acquire/Release模式测试 - happens-before关系");

        // 重置状态
        DATA_HANDLE.set(this, 0);
        READY_HANDLE.set(this, false);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        int testRuns = 100;

        for (int run = 0; run < testRuns; run++) {
            DATA_HANDLE.set(this, 0);
            READY_HANDLE.set(this, false);

            CountDownLatch runLatch = new CountDownLatch(1);

            // 生产者线程
            Thread producer = new Thread(() -> {
                try {
                    runLatch.await();
                    // 先设置数据
                    DATA_HANDLE.set(this, 42);
                    // 使用Release语义发布
                    READY_HANDLE.setRelease(this, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 消费者线程
            Thread consumer = new Thread(() -> {
                try {
                    runLatch.await();

                    // 使用Acquire语义等待
                    while (!(boolean) READY_HANDLE.getAcquire(this)) {
                        Thread.yield();
                    }

                    // 读取数据
                    int dataValue = (int) DATA_HANDLE.get(this);
                    if (dataValue == 42) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            producer.start();
            consumer.start();
            runLatch.countDown();

            producer.join();
            consumer.join();
        }

        System.out.println("  测试运行次数: " + testRuns);
        System.out.println("  成功次数: " + successCount.get());
        System.out.println("  Acquire/Release语义保证: " +
            (successCount.get() == testRuns ? "✓" : "✗"));
        System.out.println();
    }

    /**
     * 4. Volatile模式测试 - 完整内存屏障
     */
    public void testVolatileMode() throws InterruptedException {
        System.out.println("4. Volatile模式测试 - 完整内存屏障");

        // 重置状态
        DATA_HANDLE.set(this, 0);
        READY_HANDLE.set(this, false);

        AtomicInteger successCount = new AtomicInteger(0);
        int testRuns = 100;

        for (int run = 0; run < testRuns; run++) {
            DATA_HANDLE.set(this, 0);
            READY_HANDLE.set(this, false);

            CountDownLatch runLatch = new CountDownLatch(1);

            // 写线程
            Thread writer = new Thread(() -> {
                try {
                    runLatch.await();
                    // 先设置数据
                    DATA_HANDLE.set(this, 42);
                    // 使用Volatile语义设置标志
                    READY_HANDLE.setVolatile(this, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 读线程
            Thread reader = new Thread(() -> {
                try {
                    runLatch.await();

                    // 使用Volatile语义等待
                    while (!(boolean) READY_HANDLE.getVolatile(this)) {
                        Thread.yield();
                    }

                    // 读取数据 - 由于volatile的内存屏障效果，能看到数据更新
                    int dataValue = (int) DATA_HANDLE.get(this);
                    if (dataValue == 42) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            writer.start();
            reader.start();
            runLatch.countDown();

            writer.join();
            reader.join();
        }

        System.out.println("  测试运行次数: " + testRuns);
        System.out.println("  成功次数: " + successCount.get());
        System.out.println("  Volatile语义保证: " +
            (successCount.get() == testRuns ? "✓" : "✗"));
        System.out.println();
    }

    /**
     * 5. 性能对比测试
     */
    public void performanceComparison() {
        System.out.println("5. 性能对比测试");

        int iterations = 1_000_000;

        // Plain模式性能测试
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DATA_HANDLE.set(this, i);
            int value = (int) DATA_HANDLE.get(this);
        }
        long plainTime = System.nanoTime() - start;

        // Opaque模式性能测试
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DATA_HANDLE.setOpaque(this, i);
            int value = (int) DATA_HANDLE.getOpaque(this);
        }
        long opaqueTime = System.nanoTime() - start;

        // Acquire/Release模式性能测试
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DATA_HANDLE.setRelease(this, i);
            int value = (int) DATA_HANDLE.getAcquire(this);
        }
        long acquireReleaseTime = System.nanoTime() - start;

        // Volatile模式性能测试
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DATA_HANDLE.setVolatile(this, i);
            int value = (int) DATA_HANDLE.getVolatile(this);
        }
        long volatileTime = System.nanoTime() - start;

        System.out.println("  性能对比结果 (" + iterations + " 次操作):");
        System.out.printf("  Plain:           %,d ns (1.00x)%n", plainTime);
        System.out.printf("  Opaque:          %,d ns (%.2fx)%n", opaqueTime,
            (double) opaqueTime / plainTime);
        System.out.printf("  Acquire/Release: %,d ns (%.2fx)%n", acquireReleaseTime,
            (double) acquireReleaseTime / plainTime);
        System.out.printf("  Volatile:        %,d ns (%.2fx)%n", volatileTime,
            (double) volatileTime / plainTime);
        System.out.println();
    }

    /**
     * 6. 内存重排序演示
     */
    public void demonstrateMemoryReordering() throws InterruptedException {
        System.out.println("6. 内存重排序演示");

        MemoryReorderingDemo demo = new MemoryReorderingDemo();

        System.out.println("  测试Plain模式的重排序问题:");
        demo.testPlainReordering();

        System.out.println("  测试Volatile模式防止重排序:");
        demo.testVolatileOrdering();

        System.out.println();
    }

    /**
     * 内存重排序演示的内部类
     */
    private static class MemoryReorderingDemo {
        private int x = 0, y = 0;
        private int a = 0, b = 0;

        private static final VarHandle X_HANDLE, Y_HANDLE, A_HANDLE, B_HANDLE;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                X_HANDLE = lookup.findVarHandle(MemoryReorderingDemo.class, "x", int.class);
                Y_HANDLE = lookup.findVarHandle(MemoryReorderingDemo.class, "y", int.class);
                A_HANDLE = lookup.findVarHandle(MemoryReorderingDemo.class, "a", int.class);
                B_HANDLE = lookup.findVarHandle(MemoryReorderingDemo.class, "b", int.class);
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }

        public void testPlainReordering() throws InterruptedException {
            int reorderingDetected = 0;
            int totalRuns = 10000;

            for (int run = 0; run < totalRuns; run++) {
                // 重置变量
                X_HANDLE.set(this, 0);
                Y_HANDLE.set(this, 0);
                A_HANDLE.set(this, 0);
                B_HANDLE.set(this, 0);

                CountDownLatch latch = new CountDownLatch(1);

                Thread t1 = new Thread(() -> {
                    try {
                        latch.await();
                        X_HANDLE.set(this, 1);  // 可能被重排序
                        int temp = (int) Y_HANDLE.get(this);
                        A_HANDLE.set(this, temp);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                Thread t2 = new Thread(() -> {
                    try {
                        latch.await();
                        Y_HANDLE.set(this, 1);  // 可能被重排序
                        int temp = (int) X_HANDLE.get(this);
                        B_HANDLE.set(this, temp);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                t1.start();
                t2.start();
                latch.countDown();

                t1.join();
                t2.join();

                int finalA = (int) A_HANDLE.get(this);
                int finalB = (int) B_HANDLE.get(this);

                // 如果没有重排序，至少有一个应该是1
                if (finalA == 0 && finalB == 0) {
                    reorderingDetected++;
                }
            }

            System.out.println("    总测试次数: " + totalRuns);
            System.out.println("    检测到重排序次数: " + reorderingDetected);
            System.out.println("    重排序比例: " +
                String.format("%.2f%%", (double) reorderingDetected / totalRuns * 100));
        }

        public void testVolatileOrdering() throws InterruptedException {
            int reorderingDetected = 0;
            int totalRuns = 10000;

            for (int run = 0; run < totalRuns; run++) {
                // 重置变量
                X_HANDLE.set(this, 0);
                Y_HANDLE.set(this, 0);
                A_HANDLE.set(this, 0);
                B_HANDLE.set(this, 0);

                CountDownLatch latch = new CountDownLatch(1);

                Thread t1 = new Thread(() -> {
                    try {
                        latch.await();
                        X_HANDLE.setVolatile(this, 1);  // Volatile写，防止重排序
                        int temp = (int) Y_HANDLE.getVolatile(this);
                        A_HANDLE.set(this, temp);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                Thread t2 = new Thread(() -> {
                    try {
                        latch.await();
                        Y_HANDLE.setVolatile(this, 1);  // Volatile写，防止重排序
                        int temp = (int) X_HANDLE.getVolatile(this);
                        B_HANDLE.set(this, temp);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                t1.start();
                t2.start();
                latch.countDown();

                t1.join();
                t2.join();

                int finalA = (int) A_HANDLE.get(this);
                int finalB = (int) B_HANDLE.get(this);

                if (finalA == 0 && finalB == 0) {
                    reorderingDetected++;
                }
            }

            System.out.println("    总测试次数: " + totalRuns);
            System.out.println("    检测到重排序次数: " + reorderingDetected);
            System.out.println("    重排序比例: " +
                String.format("%.2f%%", (double) reorderingDetected / totalRuns * 100));
        }
    }

}
