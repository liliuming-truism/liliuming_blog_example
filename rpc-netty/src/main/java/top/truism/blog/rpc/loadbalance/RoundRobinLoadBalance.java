package top.truism.blog.rpc.loadbalance;

import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.protocol.RpcRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡（对标 Dubbo {@code RoundRobinLoadBalance}）
 *
 * <p>按顺序依次选取节点，保证每个节点获得相等数量的请求。
 * 适合节点处理能力相同、希望请求严格均分的场景。
 *
 * <p>使用 {@link AtomicInteger} 实现无锁计数，线程安全。
 */
public class RoundRobinLoadBalance implements LoadBalance {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        // 取绝对值后取余，避免 counter 溢出时出现负数索引
        int index = Math.abs(counter.getAndIncrement()) % invokers.size();
        return invokers.get(index);
    }
}
