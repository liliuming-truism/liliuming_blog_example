package top.truism.blog.rpc.cluster;

import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.loadbalance.LoadBalance;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 失败自动重试集群策略（对标 Dubbo {@code FailoverCluster}）
 *
 * <p>当调用发生<strong>传输层异常</strong>（网络超时、连接断开）时，
 * 自动换一个节点重试，直到达到最大尝试次数。
 *
 * <p>注意：仅对传输异常重试，业务异常（{@link RpcResponse#getStatus()} != SUCCESS）
 * 不触发重试，直接返回。
 *
 * <p>适合幂等操作（查询、只读 RPC）。非幂等操作（下单、转账）请使用
 * {@link FailfastClusterInvoker}。
 *
 * <p>用法：
 * <pre>{@code
 * new FailoverClusterInvoker(2)  // retries=2 → 最多 3 次尝试
 * }</pre>
 */
@Slf4j
public class FailoverClusterInvoker implements ClusterInvoker {

    /** 额外重试次数（不含首次调用），默认 2，即最多 3 次总尝试 */
    private final int retries;

    public FailoverClusterInvoker() {
        this(2);
    }

    public FailoverClusterInvoker(int retries) {
        if (retries < 0) throw new IllegalArgumentException("retries must be >= 0");
        this.retries = retries;
    }

    @Override
    public RpcResponse invoke(RpcRequest request, List<Invoker> invokers, LoadBalance loadBalance) throws Exception {
        List<Invoker> tried = new ArrayList<>();
        Exception lastException = null;

        for (int attempt = 0; attempt <= retries; attempt++) {
            // 从尚未尝试的节点中选取；若全部试过则从全量中选
            List<Invoker> candidates = invokers.stream()
                    .filter(inv -> !tried.contains(inv))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                candidates = invokers;
            }

            Invoker invoker = loadBalance.select(candidates, request);
            tried.add(invoker);

            try {
                RpcResponse response = invoker.invoke(request);
                if (attempt > 0) {
                    log.info("Failover: succeeded on attempt {}/{} via {}",
                            attempt + 1, retries + 1, invoker.getAddress());
                }
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("Failover: attempt {}/{} failed on {}: {}",
                        attempt + 1, retries + 1, invoker.getAddress(), e.getMessage());
            }
        }

        throw new RuntimeException(
                String.format("All %d attempt(s) failed for %s.%s()",
                        retries + 1, request.getInterfaceName(), request.getMethodName()),
                lastException);
    }
}
