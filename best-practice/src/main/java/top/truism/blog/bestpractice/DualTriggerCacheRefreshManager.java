package top.truism.blog.bestpractice;

import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 支持时间和数量双重触发的分布式缓存刷新管理器
 * <p>
 * 触发条件：
 * 1. 时间触发：3秒内必须处理一次
 * 2. 数量触发：消息数量达到500时立即处理
 * 3. 优先级：数量达到 > 时间到达
 */
@Slf4j
@Component
public class DualTriggerCacheRefreshManager {

    private static final long TIME_TRIGGER_MILLIS = 3000;  // 3秒
    private static final int COUNT_TRIGGER_THRESHOLD = 500; // 500条消息
    private static final long LOCK_EXPIRE_SECONDS = 10;
    private static final long SCHEDULE_EXPIRE_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduledExecutor;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks;
    private final ConcurrentHashMap<String, AtomicInteger> messageCounters;
    private final AtomicLong totalMessagesProcessed;

    public DualTriggerCacheRefreshManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.scheduledExecutor = Executors.newScheduledThreadPool(8, r -> {
            Thread t = new Thread(r, "CacheRefresh-" + System.nanoTime());
            t.setDaemon(false);
            return t;
        });
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.messageCounters = new ConcurrentHashMap<>();
        this.totalMessagesProcessed = new AtomicLong(0);
    }

    /**
     * 处理消息 - 支持双重触发
     */
    public void handleMessage(String cacheKey, String messageId) {
        handleMessage(cacheKey, messageId, TIME_TRIGGER_MILLIS);
    }

    /**
     * 处理消息 - 自定义延迟
     */
    public void handleMessage(String cacheKey, String messageId, long delayMillis) {
        try {
            // 1. 更新消息计数
            int currentCount = incrementMessageCount(cacheKey);
            log.info("缓存键 [{}] 收到消息 [{}]，当前消息数: {}", cacheKey, messageId, currentCount);

            // 2. 检查是否达到数量触发阈值
            if (currentCount >= COUNT_TRIGGER_THRESHOLD) {
                log.warn("缓存键 [{}] 消息数量达到阈值 [{}]，立即触发处理", cacheKey, COUNT_TRIGGER_THRESHOLD);
                executeRefreshImmediately(cacheKey);
                return;
            }

            // 3. 更新 Redis 中的最后触发时间
            updateLastTriggerTime(cacheKey);

            // 4. 取消已有的延迟任务
            cancelExistingTask(cacheKey);

            // 5. 调度新的延迟任务
            scheduleDelayedTask(cacheKey, delayMillis);

        } catch (Exception e) {
            log.error("处理消息失败: cacheKey={}, messageId={}", cacheKey, messageId, e);
        }
    }

    /**
     * 增加消息计数
     */
    private int incrementMessageCount(String cacheKey) {
        // 本地计数
        AtomicInteger localCounter = messageCounters.computeIfAbsent(cacheKey, k -> new AtomicInteger(0));
        int count = localCounter.incrementAndGet();

        // Redis 计数（用于分布式统计）
        String counterKey = "cache:refresh:counter:" + cacheKey;
        Long redisCount = redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.expire(counterKey, SCHEDULE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return count;
    }

    /**
     * 更新最后触发时间
     */
    private void updateLastTriggerTime(String cacheKey) {
        String scheduleKey = "cache:refresh:schedule:" + cacheKey;
        long currentTime = System.currentTimeMillis();
        redisTemplate.opsForValue().set(scheduleKey, String.valueOf(currentTime));
        redisTemplate.expire(scheduleKey, SCHEDULE_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 取消已有的延迟任务
     */
    private void cancelExistingTask(String cacheKey) {
        ScheduledFuture<?> existingTask = scheduledTasks.get(cacheKey);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
            log.debug("已取消缓存键 [{}] 的旧任务", cacheKey);
        }
    }

    /**
     * 调度延迟任务
     */
    private void scheduleDelayedTask(String cacheKey, long delayMillis) {
        ScheduledFuture<?> future = scheduledExecutor.schedule(
            () -> executeRefreshWithTimeTrigger(cacheKey, delayMillis),
            delayMillis,
            TimeUnit.MILLISECONDS
        );
        scheduledTasks.put(cacheKey, future);
        log.debug("已为缓存键 [{}] 调度延迟任务，延迟时间: {}ms", cacheKey, delayMillis);
    }

    /**
     * 时间触发的刷新执行
     */
    private void executeRefreshWithTimeTrigger(String cacheKey, long delayMillis) {
        try {
            // 检查是否有新消息到达（双重检查）
            String scheduleKey = "cache:refresh:schedule:" + cacheKey;
            String lastTriggerTimeStr = redisTemplate.opsForValue().get(scheduleKey);

            if (lastTriggerTimeStr != null) {
                long lastTriggerTime = Long.parseLong(lastTriggerTimeStr);
                long elapsed = System.currentTimeMillis() - lastTriggerTime;

                // 如果距离上次触发不足延迟时间，则跳过
                if (elapsed < delayMillis - 100) {
                    log.debug("缓存键 [{}] 有新消息到达，跳过本次处理", cacheKey);
                    return;
                }
            }

            log.info("缓存键 [{}] 时间触发条件满足（3秒），开始处理", cacheKey);
            executeRefreshWithDistributedLock(cacheKey);

        } catch (Exception e) {
            log.error("时间触发的刷新执行失败: cacheKey={}", cacheKey, e);
        }
    }

    /**
     * 立即执行刷新（数量触发）
     */
    private void executeRefreshImmediately(String cacheKey) {
        try {
            log.info("缓存键 [{}] 数量触发条件满足（500条消息），立即处理", cacheKey);

            // 取消已有的延迟任务
            cancelExistingTask(cacheKey);

            // 立即执行刷新
            executeRefreshWithDistributedLock(cacheKey);

        } catch (Exception e) {
            log.error("立即执行刷新失败: cacheKey={}", cacheKey, e);
        }
    }

    /**
     * 使用分布式锁执行刷新
     */
    private void executeRefreshWithDistributedLock(String cacheKey) {
        String lockKey = "cache:refresh:lock:" + cacheKey;
        String lockValue = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            // 尝试获取分布式锁
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                LOCK_EXPIRE_SECONDS,
                TimeUnit.SECONDS
            );

            if (Boolean.TRUE.equals(lockAcquired)) {
                log.info("缓存键 [{}] 获取分布式锁成功，开始执行刷新", cacheKey);

                try {
                    // 再次检查是否有新消息（防止获取锁期间有新消息）
                    String scheduleKey = "cache:refresh:schedule:" + cacheKey;
                    String lastTriggerTimeStr = redisTemplate.opsForValue().get(scheduleKey);

                    if (lastTriggerTimeStr != null) {
                        long lastTriggerTime = Long.parseLong(lastTriggerTimeStr);
                        long elapsed = System.currentTimeMillis() - lastTriggerTime;

                        if (elapsed < TIME_TRIGGER_MILLIS - 100) {
                            log.debug("缓存键 [{}] 获取锁后发现新消息，跳过本次处理", cacheKey);
                            return;
                        }
                    }

                    // 执行缓存刷新
                    refreshCache(cacheKey);

                    // 清理计数器
                    messageCounters.remove(cacheKey);
                    String counterKey = "cache:refresh:counter:" + cacheKey;
                    redisTemplate.delete(counterKey);

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("缓存键 [{}] 刷新完成，耗时: {}ms", cacheKey, duration);
                    totalMessagesProcessed.incrementAndGet();

                } finally {
                    // 释放锁
                    releaseLock(lockKey, lockValue);
                }
            } else {
                log.debug("缓存键 [{}] 获取分布式锁失败，等待其他实例处理", cacheKey);
            }

        } catch (Exception e) {
            log.error("执行刷新失败: cacheKey={}", cacheKey, e);
        }
    }

    /**
     * 释放分布式锁（使用 Lua 脚本保证原子性）
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            String script = "if redis.call('get', KEYS[[1]](#__1)) == ARGV[[1]](#__1) then " +
                "return redis.call('del', KEYS[[1]](#__1)) else return 0 end";

            redisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(lockKey),
                lockValue
            );
            log.debug("分布式锁已释放: {}", lockKey);
        } catch (Exception e) {
            log.error("释放分布式锁失败: lockKey={}", lockKey, e);
        }
    }

    /**
     * 缓存刷新逻辑（子类实现）
     */
    protected void refreshCache(String cacheKey) {
        log.info("执行缓存刷新: {}", cacheKey);
        // 子类实现具体的刷新逻辑
    }

    /**
     * 强制刷新
     */
    public void forceRefresh(String cacheKey) {
        executeRefreshImmediately(cacheKey);
    }

    /**
     * 取消刷新
     */
    public void cancelRefresh(String cacheKey) {
        cancelExistingTask(cacheKey);
        messageCounters.remove(cacheKey);
        log.info("已取消缓存键 [{}] 的刷新任务", cacheKey);
    }

    /**
     * 获取本地待处理任务数
     */
    public int getLocalPendingTaskCount() {
        return (int) scheduledTasks.values().stream()
            .filter(f -> !f.isDone())
            .count();
    }

    /**
     * 获取消息计数
     */
    public Integer getMessageCount(String cacheKey) {
        AtomicInteger counter = messageCounters.get(cacheKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取 Redis 中的消息计数
     */
    public Long getRedisMessageCount(String cacheKey) {
        String counterKey = "cache:refresh:counter:" + cacheKey;
        String count = redisTemplate.opsForValue().get(counterKey);
        return count != null ? Long.parseLong(count) : 0L;
    }

    /**
     * 获取已处理的总消息数
     */
    public long getTotalMessagesProcessed() {
        return totalMessagesProcessed.get();
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMessagesProcessed", totalMessagesProcessed.get());
        stats.put("pendingTasks", getLocalPendingTaskCount());
        stats.put("activeMessageCounters", messageCounters.size());
        stats.put("timeTriggerMillis", TIME_TRIGGER_MILLIS);
        stats.put("countTriggerThreshold", COUNT_TRIGGER_THRESHOLD);
        return stats;
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭缓存刷新管理器...");
        try {
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            log.info("缓存刷新管理器已关闭");
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("关闭缓存刷新管理器时被中断", e);
        }
    }
}
