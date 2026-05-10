package top.truism.blog.rpc.registry;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * ZooKeeper 注册中心（统一替代旧的 ZookeeperServiceRegistry + ZookeeperServiceDiscovery）
 *
 * <p>使用 Curator 5.x {@link CuratorCache}（推荐 API，取代已废弃的 PathChildrenCache）
 * 实现推送订阅，服务端下线后临时节点自动删除，客户端在下一次 Watch 事件中感知。
 *
 * <h3>ZK 节点结构</h3>
 * <pre>
 * /rpc
 *   └── {serviceKey}                     ← 持久节点
 *         └── providers                  ← 持久节点
 *               └── {host:port}          ← 临时节点（EPHEMERAL），节点 data = ServiceMeta 属性
 * </pre>
 *
 * <h3>节点 data 格式（properties 纯文本）</h3>
 * <pre>
 * version=1.0
 * group=groupA
 * weight=100
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 服务端
 * RegistryCenter registry = RegistryFactory.create(RegistryConfig.zookeeper("127.0.0.1:2181"));
 * RpcServer server = new RpcServer(8888, "127.0.0.1", registry);
 * server.register(HelloService.class, new HelloServiceImpl());
 * server.start();
 *
 * // 客户端
 * RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
 * HelloService svc = RpcClientProxy.builder()
 *         .invokers(dir.getInvokers())
 *         .loadBalance(new RoundRobinLoadBalance())
 *         .build().getProxy(HelloService.class);
 * }</pre>
 */
@Slf4j
public class ZookeeperRegistryCenter implements RegistryCenter {

    static final String ROOT = "/rpc";

    private final CuratorFramework client;

    /** key = serviceKey，value = 对应 providers 路径的 CuratorCache */
    private final Map<String, CuratorCache> caches = new ConcurrentHashMap<>();

    /** key = serviceKey，value = 监听器列表 */
    private final Map<String, List<ServiceChangeListener>> listeners = new ConcurrentHashMap<>();

    public ZookeeperRegistryCenter(String connectString) {
        this.client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(30_000)
                .connectionTimeoutMs(10_000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("rpc")   // 等价于 ROOT，所有路径自动加前缀 /rpc
                .build();
        client.start();
        log.info("[ZkRegistry] Connected: {}", connectString);
    }

    // ---- 服务端：注册 / 注销 ----

    @Override
    public void register(ServiceMeta meta) {
        // namespace 已设置为 "rpc"，故路径从 /{serviceKey}/providers/{address} 开始
        String path = buildProviderPath(meta.getServiceKey(), meta.getAddress());
        try {
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(path, encodeData(meta));
            log.info("[ZkRegistry] Registered: {} -> {}", meta.getServiceKey(), meta.getAddress());
        } catch (Exception e) {
            throw new RuntimeException("Failed to register to ZK: " + path, e);
        }
    }

    @Override
    public void unregister(ServiceMeta meta) {
        String path = buildProviderPath(meta.getServiceKey(), meta.getAddress());
        try {
            client.delete().quietly().forPath(path);
            log.info("[ZkRegistry] Unregistered: {} -> {}", meta.getServiceKey(), meta.getAddress());
        } catch (Exception e) {
            log.warn("[ZkRegistry] Failed to unregister: {}", path, e);
        }
    }

    // ---- 客户端：发现 / 订阅 ----

    @Override
    public List<ServiceMeta> discover(String serviceKey) {
        String providersPath = buildProvidersPath(serviceKey);
        try {
            if (client.checkExists().forPath(providersPath) == null) {
                return Collections.emptyList();
            }
            List<String> children = client.getChildren().forPath(providersPath);
            return children.stream()
                    .map(address -> parseAddress(address, serviceKey, providersPath))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover providers for: " + serviceKey, e);
        }
    }

    @Override
    public void subscribe(String serviceKey, ServiceChangeListener listener) {
        listeners.computeIfAbsent(serviceKey, k -> new CopyOnWriteArrayList<>()).add(listener);

        // 为该 serviceKey 建立一个 CuratorCache（幂等：已存在则跳过）
        caches.computeIfAbsent(serviceKey, k -> {
            String providersPath = buildProvidersPath(k);
            CuratorCache cache = CuratorCache.build(client, providersPath);

            cache.listenable().addListener((type, oldData, newData) -> {
                // 只关心子节点的创建和删除（即 providers 目录下的 host:port 节点变化）
                if (type == CuratorCacheListener.Type.NODE_CREATED
                        || type == CuratorCacheListener.Type.NODE_DELETED) {
                    String changedPath = newData != null ? newData.getPath() : oldData.getPath();
                    // 只通知 providers 子节点的变化，忽略 providers 自身节点事件
                    if (!changedPath.equals("/" + providersPath) && changedPath.contains("/providers/")) {
                        notifyListeners(k);
                    }
                }
            });

            try {
                cache.start();
                log.debug("[ZkRegistry] Watching: {}", providersPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start CuratorCache for: " + k, e);
            }
            return cache;
        });
    }

    @Override
    public void unsubscribe(String serviceKey, ServiceChangeListener listener) {
        List<ServiceChangeListener> ls = listeners.get(serviceKey);
        if (ls != null) {
            ls.remove(listener);
        }
    }

    @Override
    public void close() {
        caches.values().forEach(cache -> {
            try { cache.close(); } catch (Exception e) { log.warn("[ZkRegistry] Cache close error", e); }
        });
        client.close();
        log.info("[ZkRegistry] Closed");
    }

    // ---- 内部工具 ----

    private void notifyListeners(String serviceKey) {
        List<ServiceChangeListener> ls = listeners.get(serviceKey);
        if (ls == null || ls.isEmpty()) return;
        List<ServiceMeta> snapshot = discover(serviceKey);
        ls.forEach(l -> {
            try { l.onChanged(snapshot); }
            catch (Exception e) { log.error("[ZkRegistry] Listener error for {}", serviceKey, e); }
        });
    }

    /**
     * 节点 data 编码：properties 格式（version=x\ngroup=y\nweight=z）
     */
    private byte[] encodeData(ServiceMeta meta) {
        String data = "version=" + meta.getVersion()
                + "\ngroup=" + meta.getGroup()
                + "\nweight=" + meta.getWeight();
        return data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 从 ZK 节点读取完整 ServiceMeta（节点名 = address，节点 data = 额外属性）
     */
    private ServiceMeta parseAddress(String address, String serviceKey, String providersPath) {
        // 解析 host:port
        int colon = address.lastIndexOf(':');
        String host = address.substring(0, colon);
        int port = Integer.parseInt(address.substring(colon + 1));

        // 从 serviceKey 还原 interfaceName + version + group
        String interfaceName = serviceKey;
        String version = "";
        String group = "";
        int groupIdx = serviceKey.lastIndexOf('/');
        if (groupIdx > 0) {
            group = serviceKey.substring(groupIdx + 1);
            interfaceName = serviceKey.substring(0, groupIdx);
        }
        int versionIdx = interfaceName.lastIndexOf(':');
        if (versionIdx > 0) {
            version = interfaceName.substring(versionIdx + 1);
            interfaceName = interfaceName.substring(0, versionIdx);
        }

        // 读取节点 data 以获取 weight 等额外属性
        int weight = 100;
        try {
            byte[] data = client.getData().forPath("/" + providersPath + "/" + address);
            if (data != null && data.length > 0) {
                String props = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                for (String line : props.split("\n")) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2 && "weight".equals(kv[0].trim())) {
                        weight = Integer.parseInt(kv[1].trim());
                    }
                    // version/group 优先以节点 data 为准（覆盖 serviceKey 解析结果）
                    if (kv.length == 2 && "version".equals(kv[0].trim()) && !kv[1].trim().isEmpty()) {
                        version = kv[1].trim();
                    }
                    if (kv.length == 2 && "group".equals(kv[0].trim()) && !kv[1].trim().isEmpty()) {
                        group = kv[1].trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[ZkRegistry] Failed to read node data for {}", address, e);
        }

        return ServiceMeta.builder()
                .interfaceName(interfaceName)
                .version(version)
                .group(group)
                .host(host)
                .port(port)
                .weight(weight)
                .build();
    }

    /** providers 父目录路径（namespace 已设置，不含 /rpc 前缀） */
    private String buildProvidersPath(String serviceKey) {
        return serviceKey + "/providers";
    }

    /** 单个 provider 节点路径 */
    private String buildProviderPath(String serviceKey, String address) {
        return serviceKey + "/providers/" + address;
    }
}
