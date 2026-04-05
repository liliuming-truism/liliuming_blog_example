package top.truism.blog.bestpractice;

import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 分布式缓存刷新管理器
 * 基于 Redis 实现分布式防抖和协调
 */
@Slf4j
@Component
public class DistributedCacheRefreshManager {

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> localTasks;

    // Redis Key 前缀
    private static final String REFRESH_LOCK_PREFIX = "cache:refresh:lock:";
    private static final String REFRESH_SCHEDULE_PREFIX = "cache:refresh:schedule:";
    private static final String REFRESH_COUNTER_PREFIX = "cache:refresh:counter:";

    // 默认配置
    private static final long DEFAULT_DELAY_MILLIS = 5000; // 5秒延迟
    private static final long LOCK_EXPIRE_SECONDS = 10;    // 锁过期时间
    private static final long SCHEDULE_EXPIRE_SECONDS = 60; // 调度记录过期时间

    public DistributedCacheRefreshManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            new ThreadFactory() {
                private final AtomicLong counter = new AtomicLong(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r,
                        "distributed-cache-refresh-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            }
        );
        this.localTasks = new ConcurrentHashMap<>();
    }

    /**
     * 处理MQ消息，触发缓存刷新（分布式防抖）
     *
     * @param cacheKey  缓存键
     * @param messageId 消息ID
     */
    public void handleMessage(String cacheKey, String messageId) {
        handleMessage(cacheKey, messageId, DEFAULT_DELAY_MILLIS);
    }

    /**
     * 处理MQ消息，触发缓存刷新（自定义延迟）
     *
     * @param cacheKey    缓存键
     * @param messageId   消息ID
     * @param delayMillis 延迟时间
     */
    public void handleMessage(String cacheKey, String messageId, long delayMillis) {
        log.debug("收到缓存刷新消息: cacheKey={}, messageId={}, delay={}ms",
            cacheKey, messageId, delayMillis);

        try {
            // 1. 记录消息接收次数（用于监控）
            incrementMessageCounter(cacheKey);

            // 2. 更新最后触发时间（分布式）
            updateLastTriggerTime(cacheKey);

            // 3. 取消本地已有的调度任务
            cancelLocalTask(cacheKey);

            // 4. 调度新的刷新任务
            scheduleRefreshTask(cacheKey, delayMillis);

        } catch (Exception e) {
            log.error("处理缓存刷新消息失败: cacheKey={}, messageId={}",
                cacheKey, messageId, e);
        }
    }

    /**
     * 更新最后触发时间（Redis）
     */
    private void updateLastTriggerTime(String cacheKey) {
        String key = REFRESH_SCHEDULE_PREFIX + cacheKey;
        long now = System.currentTimeMillis();

        redisTemplate.opsForValue().set(
            key,
            String.valueOf(now),
            SCHEDULE_EXPIRE_SECONDS,
            TimeUnit.SECONDS
        );
    }

    /**
     * 获取最后触发时间
     */
    private Long getLastTriggerTime(String cacheKey) {
        String key = REFRESH_SCHEDULE_PREFIX + cacheKey;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : null;
    }

    /**
     * 增加消息计数器
     */
    private void incrementMessageCounter(String cacheKey) {
        String key = REFRESH_COUNTER_PREFIX + cacheKey;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, SCHEDULE_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 获取消息计数
     */
    public Long getMessageCount(String cacheKey) {
        String key = REFRESH_COUNTER_PREFIX + cacheKey;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /**
     * 取消本地调度任务
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
            executeRefreshWithDistributedLock(cacheKey, delayMillis);
        }, delayMillis, TimeUnit.MILLISECONDS);

        localTasks.put(cacheKey, future);
        log.debug("调度缓存刷新任务: {}, 延迟: {}ms", cacheKey, delayMillis);
    }

    /**
     * 使用分布式锁执行刷新
     */
    private void executeRefreshWithDistributedLock(String cacheKey, long delayMillis) {
        String lockKey = REFRESH_LOCK_PREFIX + cacheKey;
        String lockValue = String.valueOf(System.currentTimeMillis());

        try {
            // 1. 检查是否在延迟期间有新的触发
            Long lastTriggerTime = getLastTriggerTime(cacheKey);
            if (lastTriggerTime != null) {
                long elapsed = System.currentTimeMillis() - lastTriggerTime;
                if (elapsed < delayMillis - 100) { // 100ms 容差
                    log.debug("延迟期间有新触发，跳过本次刷新: {}, elapsed={}ms",
                        cacheKey, elapsed);
                    return;
                }
            }

            // 2. 尝试获取分布式锁
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                LOCK_EXPIRE_SECONDS,
                TimeUnit.SECONDS
            );

            if (Boolean.TRUE.equals(locked)) {
                try {
                    // 3. 再次检查（双重检查）
                    lastTriggerTime = getLastTriggerTime(cacheKey);
                    if (lastTriggerTime != null) {
                        long elapsed = System.currentTimeMillis() - lastTriggerTime;
                        if (elapsed < delayMillis - 100) {
                            log.debug("获取锁后再次检查，跳过本次刷新: {}", cacheKey);
                            return;
                        }
                    }

                    // 4. 执行刷新
                    Long messageCount = getMessageCount(cacheKey);
                    log.info("开始执行缓存刷新: {}, 累计消息数: {}", cacheKey, messageCount);

                    long startTime = System.currentTimeMillis();
                    refreshCache(cacheKey);
                    long elapsed = System.currentTimeMillis() - startTime;

                    log.info("缓存刷新完成: {}, 耗时: {}ms, 累计消息数: {}",
                        cacheKey, elapsed, messageCount);

                    // 5. 清理计数器
                    redisTemplate.delete(REFRESH_COUNTER_PREFIX + cacheKey);

                } finally {
                    // 6. 释放分布式锁（使用 Lua 脚本保证原子性）
                    releaseLock(lockKey, lockValue);
                }
            } else {
                log.debug("其他实例正在执行刷新: {}", cacheKey);
            }

        } catch (Exception e) {
            log.error("执行缓存刷新失败: {}", cacheKey, e);
        } finally {
            // 7. 清理本地任务记录
            localTasks.remove(cacheKey);
        }
    }

    /**
     * 释放分布式锁（Lua 脚本）
     */
    private void releaseLock(String lockKey, String lockValue) {
        String luaScript =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
    }

    /**
     * 实际的缓存刷新逻辑（需要子类实现或注入）
     */
    protected void refreshCache(String cacheKey) {
        // TODO: 实现具体的缓存刷新逻辑
        // 例如：
        // 1. 从数据库重新加载数据
        // 2. 更新本地缓存
        // 3. 发布缓存更新事件（通知其他节点）

        log.info("执行缓存刷新逻辑: {}", cacheKey);

        // 示例：模拟刷新操作
        try {
            Thread.sleep(100); // 模拟耗时操作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("缓存刷新完成: {}", cacheKey);
    }

    /**
     * 强制立即刷新缓存
     */
    public void forceRefresh(String cacheKey) {
        log.info("强制刷新缓存: {}", cacheKey);

        // 取消本地任务
        cancelLocalTask(cacheKey);

        // 直接执行刷新（带分布式锁）
        String lockKey = REFRESH_LOCK_PREFIX + cacheKey;
        String lockValue = String.valueOf(System.currentTimeMillis());

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
            lockKey,
            lockValue,
            LOCK_EXPIRE_SECONDS,
            TimeUnit.SECONDS
        );

        if (Boolean.TRUE.equals(locked)) {
            try {
                refreshCache(cacheKey);
            } finally {
                releaseLock(lockKey, lockValue);
            }
        } else {
            log.warn("无法获取锁，强制刷新失败: {}", cacheKey);
        }
    }

    /**
     * 取消待执行的刷新任务
     */
    public void cancelRefresh(String cacheKey) {
        cancelLocalTask(cacheKey);

        // 清理 Redis 中的调度记录
        redisTemplate.delete(REFRESH_SCHEDULE_PREFIX + cacheKey);
        redisTemplate.delete(REFRESH_COUNTER_PREFIX + cacheKey);

        log.info("取消缓存刷新: {}", cacheKey);
    }

    /**
     * 获取待处理任务数量（本地）
     */
    public int getLocalPendingTaskCount() {
        return localTasks.size();
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭分布式缓存刷新管理器...");

        // 取消所有本地任务
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
