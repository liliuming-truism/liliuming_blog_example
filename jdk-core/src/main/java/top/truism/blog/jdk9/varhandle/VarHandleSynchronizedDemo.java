package top.truism.blog.jdk9.varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用VarHandle实现synchronized效果的示例
 */
public class VarHandleSynchronizedDemo {

    // 方式1: 使用VarHandle实现简单的自旋锁
    static class VarHandleSpinLock {
        private volatile int lockState = 0; // 0表示未锁定，1表示已锁定
        private static final VarHandle LOCK_STATE;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                LOCK_STATE = lookup.findVarHandle(VarHandleSpinLock.class, "lockState", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public void lock() {
            // 自旋等待直到成功获取锁
            while (!LOCK_STATE.compareAndSet(this, 0, 1)) {
                // 可以添加Thread.onSpinWait()来优化自旋性能
                Thread.onSpinWait();
            }
        }

        public void unlock() {
            // 释放锁
            LOCK_STATE.setRelease(this, 0);
        }

        public boolean tryLock() {
            return LOCK_STATE.compareAndSet(this, 0, 1);
        }
    }

    // 方式2: 使用VarHandle实现可重入锁
    static class VarHandleReentrantLock {
        private volatile Thread owner = null;
        private volatile int lockCount = 0;

        private static final VarHandle OWNER;
        private static final VarHandle LOCK_COUNT;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                OWNER = lookup.findVarHandle(VarHandleReentrantLock.class, "owner", Thread.class);
                LOCK_COUNT = lookup.findVarHandle(VarHandleReentrantLock.class, "lockCount", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public void lock() {
            Thread currentThread = Thread.currentThread();

            // 如果当前线程已经持有锁，直接增加计数
            if (owner == currentThread) {
                LOCK_COUNT.getAndAdd(this, 1);
                return;
            }

            // 尝试获取锁
            while (!OWNER.compareAndSet(this, null, currentThread)) {
                Thread.onSpinWait();
            }

            // 设置锁计数为1
            LOCK_COUNT.setRelease(this, 1);
        }

        public void unlock() {
            Thread currentThread = Thread.currentThread();

            if (owner != currentThread) {
                throw new IllegalMonitorStateException("Current thread does not hold the lock");
            }

            int count = (int) LOCK_COUNT.getAndAdd(this, -1);

            if (count == 1) {
                // 最后一次解锁，释放所有权
                OWNER.setRelease(this, null);
            }
        }
    }

    // 方式3: 使用VarHandle实现读写锁
    static class VarHandleReadWriteLock {
        private volatile int state = 0; // 高16位表示读锁数量，低16位表示写锁数量
        private static final VarHandle STATE;
        private static final int WRITE_MASK = 0xFFFF;
        private static final int READ_SHIFT = 16;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                STATE = lookup.findVarHandle(VarHandleReadWriteLock.class, "state", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public void readLock() {
            for (;;) {
                int current = (int) STATE.getAcquire(this);
                // 如果有写锁，等待
                if ((current & WRITE_MASK) != 0) {
                    Thread.onSpinWait();
                    continue;
                }
                // 尝试增加读锁计数
                int next = current + (1 << READ_SHIFT);
                if (STATE.compareAndSet(this, current, next)) {
                    break;
                }
            }
        }

        public void readUnlock() {
            for (;;) {
                int current = (int) STATE.getAcquire(this);
                int next = current - (1 << READ_SHIFT);
                if (STATE.compareAndSet(this, current, next)) {
                    break;
                }
            }
        }

        public void writeLock() {
            for (;;) {
                int current = (int) STATE.getAcquire(this);
                // 如果有任何锁，等待
                if (current != 0) {
                    Thread.onSpinWait();
                    continue;
                }
                // 尝试获取写锁
                if (STATE.compareAndSet(this, 0, 1)) {
                    break;
                }
            }
        }

        public void writeUnlock() {
            STATE.setRelease(this, 0);
        }
    }

    // 测试用的共享资源
    static class SharedCounter {
        private int count = 0;
        private final VarHandleSpinLock lock = new VarHandleSpinLock();

        public void increment() {
            lock.lock();
            try {
                count++;
                // 模拟一些工作
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }

        public int getCount() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }
    }

    // 性能对比测试
    static class PerformanceTest {
        private static final int THREAD_COUNT = 10;
        private static final int OPERATIONS_PER_THREAD = 1000;

        // 使用synchronized的版本
        static class SynchronizedCounter {
            private int count = 0;

            public synchronized void increment() {
                count++;
            }

            public synchronized int getCount() {
                return count;
            }
        }

        // 使用VarHandle的版本
        static class VarHandleCounter {
            private volatile int count = 0;
            private static final VarHandle COUNT;

            static {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    COUNT = lookup.findVarHandle(VarHandleCounter.class, "count", int.class);
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }

            public void increment() {
                COUNT.getAndAdd(this, 1);
            }

            public int getCount() {
                return (int) COUNT.getAcquire(this);
            }
        }

        public static void runTest() throws InterruptedException {
            System.out.println("=== 性能对比测试 ===");

            // 测试synchronized版本
            long syncTime = testSynchronized();
            System.out.println("Synchronized版本耗时: " + syncTime + "ms");

            // 测试VarHandle版本
            long varHandleTime = testVarHandle();
            System.out.println("VarHandle版本耗时: " + varHandleTime + "ms");

            System.out.println("性能提升: " + ((double)(syncTime - varHandleTime) / syncTime * 100) + "%");
        }

        private static long testSynchronized() throws InterruptedException {
            SynchronizedCounter counter = new SynchronizedCounter();
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        counter.increment();
                    }
                    latch.countDown();
                });
            }

            latch.await();
            long endTime = System.currentTimeMillis();

            executor.shutdown();
            System.out.println("Synchronized最终计数: " + counter.getCount());

            return endTime - startTime;
        }

        private static long testVarHandle() throws InterruptedException {
            VarHandleCounter counter = new VarHandleCounter();
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        counter.increment();
                    }
                    latch.countDown();
                });
            }

            latch.await();
            long endTime = System.currentTimeMillis();

            executor.shutdown();
            System.out.println("VarHandle最终计数: " + counter.getCount());

            return endTime - startTime;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== VarHandle同步机制演示 ===");

        // 测试自旋锁
        System.out.println("\n1. 测试自旋锁:");
        testSpinLock();

        // 测试可重入锁
        System.out.println("\n2. 测试可重入锁:");
        testReentrantLock();

        // 测试读写锁
        System.out.println("\n3. 测试读写锁:");
        testReadWriteLock();

        // 性能对比
        System.out.println("\n4. 性能对比:");
        PerformanceTest.runTest();
    }

    private static void testSpinLock() throws InterruptedException {
        SharedCounter counter = new SharedCounter();
        CountDownLatch latch = new CountDownLatch(5);
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < 10; j++) {
                    counter.increment();
                    System.out.println("线程" + threadId + "执行第" + (j+1) + "次操作");
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        System.out.println("最终计数: " + counter.getCount());
    }

    private static void testReentrantLock() {
        VarHandleReentrantLock lock = new VarHandleReentrantLock();

        // 测试可重入性
        lock.lock();
        try {
            System.out.println("第一层锁定");
            lock.lock();
            try {
                System.out.println("第二层锁定（可重入）");
            } finally {
                lock.unlock();
                System.out.println("第二层解锁");
            }
        } finally {
            lock.unlock();
            System.out.println("第一层解锁");
        }
    }

    private static void testReadWriteLock() throws InterruptedException {
        VarHandleReadWriteLock rwLock = new VarHandleReadWriteLock();
        CountDownLatch latch = new CountDownLatch(6);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        // 启动多个读线程
        for (int i = 0; i < 4; i++) {
            final int readerId = i;
            executor.submit(() -> {
                rwLock.readLock();
                try {
                    System.out.println("读线程" + readerId + "正在读取数据");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    rwLock.readUnlock();
                    System.out.println("读线程" + readerId + "完成读取");
                    latch.countDown();
                }
            });
        }

        // 启动写线程
        for (int i = 0; i < 2; i++) {
            final int writerId = i;
            executor.submit(() -> {
                rwLock.writeLock();
                try {
                    System.out.println("写线程" + writerId + "正在写入数据");
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    rwLock.writeUnlock();
                    System.out.println("写线程" + writerId + "完成写入");
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
    }
}
