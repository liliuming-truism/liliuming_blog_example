package top.truism.blog.redis.api;

import top.truism.blog.redis.bootstrap.RedisClient;
import top.truism.blog.redis.command.StringCommands;
import top.truism.blog.redis.command.StringCommandsImpl;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class RedisStringClient implements AutoCloseable {

    private final RedisClient redisClient;
    private final StringCommands stringCommands;

    public RedisStringClient(String host, int port) {
        this.redisClient = new RedisClient(host, port);
        this.stringCommands = new StringCommandsImpl(redisClient);
    }

    public void connect() throws InterruptedException {
        redisClient.connect();
    }

    public CompletableFuture<String> get(String key) {
        return stringCommands.get(key);
    }

    public CompletableFuture<Boolean> set(String key, String value) {
        return stringCommands.set(key, value);
    }

    public CompletableFuture<Boolean> set(String key, String value, Duration expire) {
        return stringCommands.set(key, value, expire);
    }

    public CompletableFuture<Long> del(String... keys) {
        return stringCommands.del(keys);
    }

    public CompletableFuture<Long> exists(String... keys) {
        return stringCommands.exists(keys);
    }

    @Override
    public void close() {
        redisClient.shutdown();
    }
}