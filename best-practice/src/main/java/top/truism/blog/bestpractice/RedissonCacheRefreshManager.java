package top.truism.blog.bestpractice;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Component;

/**
 * 基于 Redisson 的分布式缓存刷新管理器
 */
@Slf4j
@Component
public class RedissonCacheRefreshManager {

    private final RedissonClient redissonClient;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> localTasks;

    private static final String REFRESH_LOCK_PREFIX = "cache:refresh:lock:";
    private static final String REFRESH_SCHEDULE_PREFIX = "cache:refresh:schedule:";
    private static final String REFRESH_COUNTER_PREFIX = "cache:refresh:counter:";

    private static final long DEFAULT_DELAY_MILLIS = 5000;
    private static final long LOCK_WAIT_TIME = 100;
    private static final long LOCK_LEASE_TIME = 10000;

    public RedissonCacheRefreshManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            new ThreadFactory() {
                private final AtomicLong counter = new AtomicLong(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r,
                        "redisson-cache-refresh-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            }
        );
        this.localTasks = new ConcurrentHashMap<>();
    }

    /**
     * 处理MQ消息
     */
    public void handleMessage(String cacheKey, String messageId) {
        handleMessage(cacheKey, messageId, DEFAULT_DELAY_MILLIS);
    }

    /**
     * 处理MQ消息（自定义延迟）
     */
    public void handleMessage(String cacheKey, String messageId, long delayMillis) {
        log.debug("收到缓存刷新消息: cacheKey={}, messageId={}, delay={}ms",
            cacheKey, messageId, delayMillis);

        try {
            // 1. 增加消息计数
            RAtomicLong counter = redissonClient.getAtomicLong(
                REFRESH_COUNTER_PREFIX + cacheKey);
            counter.incrementAndGet();
            counter.expire(60, TimeUnit.SECONDS);

            // 2. 更新最后触发时间
            RBucket<Long> lastTriggerBucket = redissonClient.getBucket(
                REFRESH_SCHEDULE_PREFIX + cacheKey);
            lastTriggerBucket.set(System.currentTimeMillis(), 60, TimeUnit.SECONDS);

            // 3. 取消本地任务
            cancelLocalTask(cacheKey);

            // 4. 调度新任务
            scheduleRefreshTask(cacheKey, delayMillis);

        } catch (Exception e) {
            log.error("处理缓存刷新消息失败: cacheKey={}, messageId={}",
                cacheKey, messageId, e);
        }
    }

    /**
     * 取消本地任务
     */
    private void cancelLocalTask(String cacheKey) {
        ScheduledFuture<?> future = localTasks.remove(cacheKey);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            log.debug("取消本地调度任务: {}", cacheKey);
        }
    }

    /**
     * 调度刷新任务
     */
    private void scheduleRefreshTask(String cacheKey, long delayMillis) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            executeRefreshWithLock(cacheKey, delayMillis);
        }, delayMillis, TimeUnit.MILLISECONDS);

        localTasks.put(cacheKey, future);
        log.debug("调度缓存刷新任务: {}, 延迟: {}ms", cacheKey, delayMillis);
    }

    /**
     * 使用分布式锁执行刷新
     */
    private void executeRefreshWithLock(String cacheKey, long delayMillis) {
        String lockKey = REFRESH_LOCK_PREFIX + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 1. 检查是否在延迟期间有新的触发
            RBucket<Long> lastTriggerBucket = redissonClient.getBucket(
                REFRESH_SCHEDULE_PREFIX + cacheKey);
            Long lastTriggerTime = lastTriggerBucket.get();

            if (lastTriggerTime != null) {
                long elapsed = System.currentTimeMillis() - lastTriggerTime;
                if (elapsed < delayMillis - 100) {
                    log.debug("延迟期间有新触发，跳过本次刷新: {}, elapsed={}ms",
                        cacheKey, elapsed);
                    return;
                }
            }

            // 2. 尝试获取分布式锁（非阻塞）
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME,
                TimeUnit.MILLISECONDS);

            if (locked) {
                try {
                    // 3. 双重检查
                    lastTriggerTime = lastTriggerBucket.get();
                    if (lastTriggerTime != null) {
                        long elapsed = System.currentTimeMillis() - lastTriggerTime;
                        if (elapsed < delayMillis - 100) {
                            log.debug("获取锁后再次检查，跳过本次刷新: {}", cacheKey);
                            return;
                        }
                    }

                    // 4. 执行刷新
                    RAtomicLong counter = redissonClient.getAtomicLong(
                        REFRESH_COUNTER_PREFIX + cacheKey);
                    long messageCount = counter.get();

                    log.info("开始执行缓存刷新: {}, 累计消息数: {}", cacheKey, messageCount);

                    long startTime = System.currentTimeMillis();
                    refreshCache(cacheKey);
                    long elapsed = System.currentTimeMillis() - startTime;

                    log.info("缓存刷新完成: {}, 耗时: {}ms, 累计消息数: {}",
                        cacheKey, elapsed, messageCount);

                    // 5. 清理计数器
                    counter.delete();

                } finally {
                    // 6. 释放锁
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.debug("其他实例正在执行刷新: {}", cacheKey);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: {}", cacheKey, e);
        } catch (Exception e) {
            log.error("执行缓存刷新失败: {}", cacheKey, e);
        } finally {
            localTasks.remove(cacheKey);
        }
    }

    /**
     * 实际的缓存刷新逻辑
     */
    protected void refreshCache(String cacheKey) {
        // TODO: 实现具体的缓存刷新逻辑
        log.info("执行缓存刷新逻辑: {}", cacheKey);

        try {
            Thread.sleep(100); // 模拟耗时操作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 强制立即刷新
     */
    public void forceRefresh(String cacheKey) {
        log.info("强制刷新缓存: {}", cacheKey);

        cancelLocalTask(cacheKey);

        String lockKey = REFRESH_LOCK_PREFIX + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME,
                TimeUnit.MILLISECONDS);

            if (locked) {
                try {
                    refreshCache(cacheKey);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.warn("无法获取锁，强制刷新失败: {}", cacheKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("强制刷新被中断: {}", cacheKey, e);
        }
    }

    /**
     * 获取消息计数
     */
    public long getMessageCount(String cacheKey) {
        RAtomicLong counter = redissonClient.getAtomicLong(
            REFRESH_COUNTER_PREFIX + cacheKey);
        return counter.get();
    }

    /**
     * 获取本地待处理任务数
     */
    public int getLocalPendingTaskCount() {
        return localTasks.size();
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭分布式缓存刷新管理器...");

        localTasks.values().forEach(future -> future.cancel(false));
        localTasks.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("分布式缓存刷新管理器已关闭");
    }
}

