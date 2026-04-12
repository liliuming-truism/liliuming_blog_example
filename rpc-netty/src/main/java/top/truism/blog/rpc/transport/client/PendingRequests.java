package top.truism.blog.rpc.transport.client;

import top.truism.blog.rpc.protocol.RpcMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挂起请求表
 * <p>
 * 客户端发送请求后，将 requestId -> CompletableFuture 存入此表；
 * 收到响应时，根据 requestId 找到对应的 Future 并完成它，实现异步转同步。
 */
public class PendingRequests {

    private final ConcurrentHashMap<Long, CompletableFuture<RpcMessage>> pending = new ConcurrentHashMap<>();

    public void put(long requestId, CompletableFuture<RpcMessage> future) {
        pending.put(requestId, future);
    }

    /**
     * 用响应消息完成对应的 Future，并从表中移除
     */
    public void complete(RpcMessage response) {
        CompletableFuture<RpcMessage> future = pending.remove(response.getRequestId());
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * 用异常完成所有挂起的 Future（连接断开时调用）
     */
    public void failAll(Throwable cause) {
        pending.forEach((id, future) -> future.completeExceptionally(cause));
        pending.clear();
    }
}
