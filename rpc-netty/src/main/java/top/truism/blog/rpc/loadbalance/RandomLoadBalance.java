package top.truism.blog.rpc.loadbalance;

import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.protocol.RpcRequest;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡（Dubbo 默认策略，对标 {@code RandomLoadBalance}）
 *
 * <p>每次从候选列表中随机选取一个节点。节点数量较多时，概率趋于均匀分布。
 * 无状态、线程安全，实现最简单，适合节点性能相近的场景。
 */
public class RandomLoadBalance implements LoadBalance {

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        return invokers.get(ThreadLocalRandom.current().nextInt(invokers.size()));
    }
}
