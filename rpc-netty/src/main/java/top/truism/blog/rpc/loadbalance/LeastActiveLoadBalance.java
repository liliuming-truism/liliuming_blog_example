package top.truism.blog.rpc.loadbalance;

import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.protocol.RpcRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 最少活跃数负载均衡（对标 Dubbo {@code LeastActiveLoadBalance}）
 *
 * <p>优先选择当前<strong>在途（in-flight）请求数最少</strong>的节点。
 * 响应快的节点处理完请求更早，活跃数下降更快，因此自然获得更多流量——
 * 相当于自适应的性能加权策略。
 *
 * <p>若存在多个活跃数相同的最低节点，则从中随机选一个，避免热点。
 */
public class LeastActiveLoadBalance implements LoadBalance {

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        int minActive = Integer.MAX_VALUE;
        List<Invoker> leastActives = new ArrayList<>();

        for (Invoker invoker : invokers) {
            int active = invoker.getActiveCount();
            if (active < minActive) {
                minActive = active;
                leastActives.clear();
                leastActives.add(invoker);
            } else if (active == minActive) {
                leastActives.add(invoker);
            }
        }

        // 多个最低节点时随机选取
        if (leastActives.size() == 1) {
            return leastActives.get(0);
        }
        return leastActives.get(ThreadLocalRandom.current().nextInt(leastActives.size()));
    }
}
