package top.truism.blog.rpc.registry;

import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.cluster.NettyInvoker;
import top.truism.blog.rpc.transport.client.RpcClient;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 动态 Invoker 列表（对标 Dubbo {@code RegistryDirectory}）
 *
 * <p>持有一个 {@link RegistryCenter} 订阅，当服务提供者上下线时自动更新内部的
 * {@link Invoker} 列表：
 * <ul>
 *   <li>新增节点 → 建立连接，追加到列表</li>
 *   <li>删除节点 → 关闭连接，从列表移除</li>
 *   <li>已存在节点 → 复用连接（不重建）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * RegistryCenter registry = RegistryFactory.create(RegistryConfig.zookeeper("127.0.0.1:2181"));
 * RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
 *
 * HelloService svc = RpcClientProxy.builder()
 *         .invokers(dir.getInvokers())   // 拿快照，配合 Failover 可在调用时容错
 *         .loadBalance(new RoundRobinLoadBalance())
 *         .cluster(new FailoverClusterInvoker(2))
 *         .build()
 *         .getProxy(HelloService.class);
 * }</pre>
 *
 * <p><b>线程安全：</b>{@link #getInvokers()} 返回 {@link Collections#unmodifiableList} 快照，
 * 更新由 {@link ServiceChangeListener} 在注册中心的通知线程执行，
 * volatile 保证可见性。
 */
@Slf4j
public class RegistryDirectory implements Closeable {

    private final RegistryCenter registryCenter;
    private final String serviceKey;
    private final long timeoutSeconds;
    private final ServiceChangeListener changeListener;

    /** address → 已连接的 RpcClient，用于连接复用 */
    private final Map<String, RpcClient> clientPool = new ConcurrentHashMap<>();

    /** 当前存活的 Invoker 列表（volatile 保证 getInvokers 读到最新值） */
    private volatile List<Invoker> invokers = Collections.emptyList();

    /**
     * 构造时执行一次 pull 发现，并注册 push 订阅。
     *
     * @param registryCenter 注册中心
     * @param serviceKey     服务键（见 {@link ServiceMeta#getServiceKey()}）
     * @param timeoutSeconds 单次 RPC 调用超时秒数
     * @throws InterruptedException 初始连接被中断
     */
    public RegistryDirectory(RegistryCenter registryCenter, String serviceKey, long timeoutSeconds)
            throws InterruptedException {
        this.registryCenter  = registryCenter;
        this.serviceKey      = serviceKey;
        this.timeoutSeconds  = timeoutSeconds;
        this.changeListener  = this::rebuildInvokers;

        // 初始 pull
        List<ServiceMeta> initial = registryCenter.discover(serviceKey);
        rebuildInvokers(initial);

        // 注册 push 订阅
        registryCenter.subscribe(serviceKey, changeListener);
        log.info("[RegistryDirectory] Initialized: {} with {} provider(s)", serviceKey, invokers.size());
    }

    /**
     * 返回当前存活 Invoker 列表的不可变快照。
     *
     * <p>调用方（{@link top.truism.blog.rpc.cluster.ClusterInvoker}）拿到快照后独立完成本次调用，
     * 即使快照与注册中心有短暂不一致，Failover 策略可自动重试其他节点。
     */
    public List<Invoker> getInvokers() {
        return invokers;
    }

    /**
     * 取消订阅并关闭所有托管连接。
     */
    @Override
    public void close() {
        registryCenter.unsubscribe(serviceKey, changeListener);
        clientPool.values().forEach(client -> {
            try { client.shutdown(); } catch (Exception e) { log.warn("Client shutdown error", e); }
        });
        clientPool.clear();
        invokers = Collections.emptyList();
        log.info("[RegistryDirectory] Closed: {}", serviceKey);
    }

    // ---- 内部：重建 Invoker 列表 ----

    /**
     * 当注册中心推送新的提供者列表时调用（也用于初始 pull）。
     *
     * <p>策略：
     * <ol>
     *   <li>新增地址 → 新建 {@link RpcClient} 并连接</li>
     *   <li>已存在地址 → 复用 {@link RpcClient}</li>
     *   <li>不在新列表中的地址 → shutdown 并从连接池移除</li>
     * </ol>
     */
    private void rebuildInvokers(List<ServiceMeta> metas) {
        Set<String> newAddresses = metas.stream()
                .map(ServiceMeta::getAddress)
                .collect(Collectors.toSet());

        // 移除已下线节点
        clientPool.entrySet().removeIf(entry -> {
            if (!newAddresses.contains(entry.getKey())) {
                entry.getValue().shutdown();
                log.info("[RegistryDirectory] Provider removed: {}", entry.getKey());
                return true;
            }
            return false;
        });

        // 为新节点建立连接
        for (ServiceMeta meta : metas) {
            clientPool.computeIfAbsent(meta.getAddress(), addr -> {
                try {
                    RpcClient client = new RpcClient(meta.getHost(), meta.getPort());
                    client.connect();
                    log.info("[RegistryDirectory] Provider added: {}", addr);
                    return client;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Failed to connect to " + addr, e);
                }
            });
        }

        // 按 ServiceMeta 顺序构建 Invoker 列表，保证权重顺序一致
        List<Invoker> newInvokers = metas.stream()
                .filter(meta -> clientPool.containsKey(meta.getAddress()))
                .map(meta -> (Invoker) new NettyInvoker(clientPool.get(meta.getAddress()), timeoutSeconds))
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));

        this.invokers = Collections.unmodifiableList(newInvokers);
        log.info("[RegistryDirectory] Invokers updated: {} provider(s) for {}", newInvokers.size(), serviceKey);
    }
}
