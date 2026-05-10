package top.truism.blog.redis.command;

import top.truism.blog.redis.bootstrap.RedisClient;
import top.truism.blog.redis.codec.RespValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringCommandsImpl implements StringCommands {

    private final RedisClient redisClient;

    public StringCommandsImpl(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public CompletableFuture<String> get(String key) {
        return redisClient.sendCommand(
                RespValue.array(List.of(
                        RespValue.bulkString("GET"),
                        RespValue.bulkString(key)
                ))
        ).thenApply(v -> v.isNull() ? null : v.asString());
    }

    @Override
    public CompletableFuture<Boolean> set(String key, String value) {
        return redisClient.sendCommand(
                RespValue.array(List.of(
                        RespValue.bulkString("SET"),
                        RespValue.bulkString(key),
                        RespValue.bulkString(value)
                ))
        ).thenApply(v -> "OK".equals(v.asString()));
    }

    @Override
    public CompletableFuture<Boolean> set(String key, String value, Duration expire) {
        return redisClient.sendCommand(
                RespValue.array(List.of(
                        RespValue.bulkString("SET"),
                        RespValue.bulkString(key),
                        RespValue.bulkString(value),
                        RespValue.bulkString("EX"),
                        RespValue.bulkString(String.valueOf(expire.getSeconds()))
                ))
        ).thenApply(v -> "OK".equals(v.asString()));
    }

    @Override
    public CompletableFuture<Long> del(String... keys) {
        List<RespValue> list = new ArrayList<>();
        list.add(RespValue.bulkString("DEL"));
        for (String key : keys) {
            list.add(RespValue.bulkString(key));
        }
        return redisClient.sendCommand(RespValue.array(list))
                .thenApply(RespValue::asLong);
    }

    @Override
    public CompletableFuture<Long> exists(String... keys) {
        List<RespValue> list = new ArrayList<>();
        list.add(RespValue.bulkString("EXISTS"));
        for (String key : keys) {
            list.add(RespValue.bulkString(key));
        }
        return redisClient.sendCommand(RespValue.array(list))
                .thenApply(RespValue::asLong);
    }
}