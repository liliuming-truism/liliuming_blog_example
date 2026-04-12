package top.truism.blog.rpc.cluster;

import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

/**
 * 服务节点抽象（对标 Dubbo {@code com.alibaba.dubbo.rpc.Invoker}）
 *
 * <p>每个 {@code Invoker} 代表一个可调用的远端服务节点（host:port）。
 * 客户端可持有多个 {@code Invoker}，由 {@link top.truism.blog.rpc.loadbalance.LoadBalance}
 * 选取，再由 {@link ClusterInvoker} 包裹容错逻辑后执行。
 *
 * <p>{@link #getActiveCount()} 返回当前正在处理中的请求数，
 * 供 {@link top.truism.blog.rpc.loadbalance.LeastActiveLoadBalance} 使用。
 */
public interface Invoker {

    /**
     * 执行 RPC 调用，返回响应
     *
     * @throws Exception 网络异常、超时等传输层错误（业务错误通过 {@link RpcResponse#getStatus()} 体现）
     */
    RpcResponse invoke(RpcRequest request) throws Exception;

    /** 节点地址，格式 {@code host:port} */
    String getAddress();

    /** 节点是否可用 */
    boolean isAvailable();

    /** 当前在途（in-flight）请求数，用于最少活跃数负载均衡 */
    int getActiveCount();
}
