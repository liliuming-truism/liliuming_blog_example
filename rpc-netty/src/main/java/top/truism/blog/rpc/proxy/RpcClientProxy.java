package top.truism.blog.rpc.proxy;

import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.cluster.*;
import top.truism.blog.rpc.filter.FilterChain;
import top.truism.blog.rpc.loadbalance.LoadBalance;
import top.truism.blog.rpc.loadbalance.RandomLoadBalance;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;
import top.truism.blog.rpc.transport.client.RpcClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * RPC 客户端动态代理（对标 Dubbo {@code InvokerInvocationHandler}）
 *
 * <p>让调用方像调用本地接口一样使用远程服务，透明封装：
 * <ol>
 *   <li>FilterChain — 前后置拦截（AccessLog、TPS 限流等）</li>
 *   <li>ClusterInvoker — 集群容错（Failover 重试 / Failfast）</li>
 *   <li>LoadBalance — 节点选择（Random / RoundRobin / LeastActive / ConsistentHash）</li>
 *   <li>NettyInvoker — 实际网络调用</li>
 * </ol>
 *
 * <p><b>单节点（向后兼容）用法：</b>
 * <pre>{@code
 * RpcClientProxy proxy = new RpcClientProxy(client);
 * HelloService svc = proxy.getProxy(HelloService.class);
 * }</pre>
 *
 * <p><b>多节点 + 自定义策略用法：</b>
 * <pre>{@code
 * HelloService svc = RpcClientProxy.builder()
 *         .invokers(List.of(invoker1, invoker2, invoker3))
 *         .loadBalance(new RoundRobinLoadBalance())
 *         .cluster(new FailoverClusterInvoker(2))
 *         .filterChain(FilterChain.of(new AccessLogFilter(), new TpsLimitFilter(200)))
 *         .build()
 *         .getProxy(HelloService.class);
 * }</pre>
 */
@Slf4j
public class RpcClientProxy implements InvocationHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 5;

    private final List<Invoker> invokers;
    private final LoadBalance loadBalance;
    private final ClusterInvoker cluster;
    private final FilterChain filterChain;
    private final String version;

    // ---- 向后兼容：单节点构造器 ----

    public RpcClientProxy(RpcClient client) {
        this(client, "", DEFAULT_TIMEOUT_SECONDS);
    }

    public RpcClientProxy(RpcClient client, String version, long timeoutSeconds) {
        this.invokers    = List.of(new NettyInvoker(client, timeoutSeconds));
        this.loadBalance = new RandomLoadBalance();
        this.cluster     = new FailfastClusterInvoker();
        this.filterChain = FilterChain.EMPTY;
        this.version     = version;
    }

    // ---- Builder 构造器 ----

    private RpcClientProxy(Builder b) {
        this.invokers    = List.copyOf(b.invokers);
        this.loadBalance = b.loadBalance;
        this.cluster     = b.cluster;
        this.filterChain = b.filterChain;
        this.version     = b.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- 核心：代理工厂 ----

    /**
     * 为指定接口创建动态代理
     *
     * @throws IllegalArgumentException 若 interfaceClass 不是接口
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(interfaceClass.getName() + " is not an interface");
        }
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                this);
    }

    // ---- InvocationHandler ----

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 的基础方法（equals/hashCode/toString）不走远程调用
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameterTypes(method.getParameterTypes())
                .parameters(args)
                .version(version)
                .build();

        log.debug("RPC call: {}.{}()", request.getInterfaceName(), request.getMethodName());

        // Filter → ClusterInvoker(LoadBalance → NettyInvoker)
        RpcResponse response = filterChain.execute(request,
                req -> cluster.invoke(req, invokers, loadBalance));

        if (response.getStatus() != RpcResponse.STATUS_SUCCESS) {
            throw new RuntimeException("RPC call failed: " + response.getErrorMessage());
        }
        return response.getData();
    }

    // ---- Builder ----

    public static final class Builder {

        private List<Invoker> invokers;
        private LoadBalance   loadBalance = new RandomLoadBalance();
        private ClusterInvoker cluster    = new FailfastClusterInvoker();
        private FilterChain   filterChain = FilterChain.EMPTY;
        private String        version     = "";

        /**
         * 指定多个服务节点
         *
         * <p>节点由 {@link NettyInvoker} 包装，需提前调用 {@link RpcClient#connect()}。
         */
        public Builder invokers(List<Invoker> invokers) {
            this.invokers = invokers;
            return this;
        }

        /** 负载均衡策略，默认 {@link RandomLoadBalance} */
        public Builder loadBalance(LoadBalance loadBalance) {
            this.loadBalance = loadBalance;
            return this;
        }

        /** 集群容错策略，默认 {@link FailfastClusterInvoker} */
        public Builder cluster(ClusterInvoker cluster) {
            this.cluster = cluster;
            return this;
        }

        /** 过滤器链 */
        public Builder filterChain(FilterChain filterChain) {
            this.filterChain = filterChain;
            return this;
        }

        /** 服务版本 */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public RpcClientProxy build() {
            if (invokers == null || invokers.isEmpty()) {
                throw new IllegalStateException("At least one Invoker is required");
            }
            return new RpcClientProxy(this);
        }
    }
}
