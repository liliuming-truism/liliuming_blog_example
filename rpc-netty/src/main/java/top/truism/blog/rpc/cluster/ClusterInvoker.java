package top.truism.blog.rpc.cluster;

import top.truism.blog.rpc.loadbalance.LoadBalance;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

import java.util.List;

/**
 * 集群容错策略接口（对标 Dubbo {@code com.alibaba.dubbo.rpc.cluster.Cluster}）
 *
 * <p>将多个 {@link Invoker} + {@link LoadBalance} 组合，提供统一的容错语义：
 * <ul>
 *   <li>{@link FailoverClusterInvoker} — 失败自动重试（默认，适合幂等读操作）</li>
 *   <li>{@link FailfastClusterInvoker} — 快速失败（适合非幂等写操作）</li>
 * </ul>
 */
public interface ClusterInvoker {

    /**
     * 使用给定负载均衡策略，从 invokers 中选取节点发起调用，按本策略处理失败。
     *
     * @param request     RPC 请求
     * @param invokers    可用节点列表
     * @param loadBalance 节点选取策略
     * @return RPC 响应
     * @throws Exception 所有重试耗尽后仍失败时抛出
     */
    RpcResponse invoke(RpcRequest request, List<Invoker> invokers, LoadBalance loadBalance) throws Exception;
}
