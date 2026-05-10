package top.truism.blog.redis.bootstrap;

import top.truism.blog.redis.codec.RespValue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PendingRequests {

    private final ConcurrentHashMap<Long, CompletableFuture<RespValue>> pending = new ConcurrentHashMap<>();

    public void put(long requestId, CompletableFuture<RespValue> future) {
        pending.put(requestId, future);
    }

    public void complete(long requestId, RespValue response) {
        CompletableFuture<RespValue> future = pending.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    public void failAll(Throwable cause) {
        pending.forEach((id, future) -> future.completeExceptionally(cause));
        pending.clear();
    }

    public int size() {
        return pending.size();
    }

    public CompletableFuture<RespValue> remove(long requestId) {
        return pending.remove(requestId);
    }
}