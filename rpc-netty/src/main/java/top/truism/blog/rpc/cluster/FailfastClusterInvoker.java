package top.truism.blog.rpc.cluster;

import top.truism.blog.rpc.loadbalance.LoadBalance;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

import java.util.List;

/**
 * 快速失败集群策略（对标 Dubbo {@code FailfastCluster}）
 *
 * <p>只发起一次调用，失败后立即抛出异常，不重试。
 *
 * <p>适合非幂等写操作（新增、修改、扣款），防止重复执行。
 */
public class FailfastClusterInvoker implements ClusterInvoker {

    @Override
    public RpcResponse invoke(RpcRequest request, List<Invoker> invokers, LoadBalance loadBalance) throws Exception {
        Invoker invoker = loadBalance.select(invokers, request);
        return invoker.invoke(request);
    }
}
