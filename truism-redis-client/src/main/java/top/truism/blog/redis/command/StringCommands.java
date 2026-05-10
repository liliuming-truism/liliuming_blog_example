package top.truism.blog.redis.command;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface StringCommands {

    CompletableFuture<String> get(String key);

    CompletableFuture<Boolean> set(String key, String value);

    CompletableFuture<Boolean> set(String key, String value, Duration expire);

    CompletableFuture<Long> del(String... keys);

    CompletableFuture<Long> exists(String... keys);
}