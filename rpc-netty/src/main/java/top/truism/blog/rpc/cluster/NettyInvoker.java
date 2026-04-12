package top.truism.blog.rpc.cluster;

import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;
import top.truism.blog.rpc.transport.client.RpcClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Netty 的 Invoker 实现
 *
 * <p>封装单个 {@link RpcClient}（即一条到服务端的连接），
 * 提供 {@link #getActiveCount()} 供最少活跃数负载均衡使用。
 *
 * <p>用法：
 * <pre>{@code
 * RpcClient client = new RpcClient("127.0.0.1", 8888);
 * client.connect();
 * Invoker invoker = new NettyInvoker(client, 5);
 * }</pre>
 */
@Slf4j
public class NettyInvoker implements Invoker {

    private final RpcClient client;
    private final String address;
    private final long timeoutSeconds;
    private final AtomicInteger activeCount = new AtomicInteger(0);

    public NettyInvoker(RpcClient client, long timeoutSeconds) {
        this.client = client;
        this.address = client.getAddress();
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public RpcResponse invoke(RpcRequest request) throws Exception {
        activeCount.incrementAndGet();
        try {
            return client.sendRequest(request).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException(
                    String.format("RPC timeout after %ds: %s.%s() -> %s",
                            timeoutSeconds, request.getInterfaceName(), request.getMethodName(), address), e);
        } finally {
            activeCount.decrementAndGet();
        }
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public boolean isAvailable() {
        return client.isConnected();
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }
}
