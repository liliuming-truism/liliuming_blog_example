package top.truism.blog.rpc.loadbalance;

import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.protocol.RpcRequest;

import java.util.List;

/**
 * 负载均衡策略接口（对标 Dubbo {@code com.alibaba.dubbo.rpc.cluster.LoadBalance}）
 *
 * <p>从候选 Invoker 列表中按照特定策略选取一个节点来处理本次请求。
 *
 * <p>内置四种实现：
 * <ul>
 *   <li>{@link RandomLoadBalance}       — 随机（默认，无状态，最简单）</li>
 *   <li>{@link RoundRobinLoadBalance}   — 轮询（均匀分散流量）</li>
 *   <li>{@link LeastActiveLoadBalance}  — 最少活跃数（响应快的节点获得更多请求）</li>
 *   <li>{@link ConsistentHashLoadBalance} — 一致性哈希（相同参数路由到同一节点）</li>
 * </ul>
 */
public interface LoadBalance {

    /**
     * 从候选列表中选取一个 Invoker
     *
     * @param invokers 当前可用节点列表，非空
     * @param request  本次 RPC 请求（可用于哈希路由）
     * @return 选中的 Invoker
     */
    Invoker select(List<Invoker> invokers, RpcRequest request);
}
