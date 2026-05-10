package top.truism.blog.rpc.registry;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Nacos 注册中心（骨架实现）
 *
 * <p>展示如何基于 {@link RegistryCenter} 接口接入 Alibaba Nacos，
 * 以验证抽象层的可扩展性。
 *
 * <h3>接入步骤</h3>
 * <ol>
 *   <li>在 pom.xml 中取消以下依赖的注释：
 *     <pre>{@code
 * <dependency>
 *     <groupId>com.alibaba.nacos</groupId>
 *     <artifactId>nacos-client</artifactId>
 *     <version>2.5.1</version>
 * </dependency>
 *     }</pre>
 *   </li>
 *   <li>取消本类中所有 {@code // >>} 前缀的注释代码行</li>
 *   <li>删除构造器中的 {@code throw new UnsupportedOperationException(...)} 语句</li>
 * </ol>
 *
 * <h3>Nacos 注册发现核心 API</h3>
 * <pre>{@code
 * // 服务注册
 * Instance instance = new Instance();
 * instance.setIp(meta.getHost());
 * instance.setPort(meta.getPort());
 * instance.setWeight(meta.getWeight());
 * instance.setMetadata(Map.of("version", meta.getVersion(), "group", meta.getGroup()));
 * namingService.registerInstance(meta.getServiceKey(), instance);
 *
 * // 服务发现（pull）
 * List<Instance> instances = namingService.getAllInstances(serviceKey);
 *
 * // 订阅推送（push）
 * namingService.subscribe(serviceKey, event -> {
 *     if (event instanceof NamingEvent ne) {
 *         List<Instance> latest = ne.getInstances();
 *         // 转换 Instance → ServiceMeta，通知 ServiceChangeListener
 *     }
 * });
 * }</pre>
 *
 * <p>对比 ZookeeperRegistryCenter：
 * <ul>
 *   <li>Nacos 内置健康检查（心跳/TTL），无需临时节点机制</li>
 *   <li>Nacos 支持持久化服务（非临时），适合配置型服务</li>
 *   <li>Nacos 订阅是长轮询（UDP 推送）+ 定时兜底，ZK 是 Watcher 推送</li>
 * </ul>
 */
@Slf4j
public class NacosRegistryCenter implements RegistryCenter {

    // >> private NamingService namingService;
    // >> private final Map<ServiceChangeListener, EventListener> subscriptions = new ConcurrentHashMap<>();

    /**
     * @param serverAddr Nacos Server 地址，如 {@code "127.0.0.1:8848"}
     * @throws UnsupportedOperationException 未添加 nacos-client 依赖时抛出
     */
    public NacosRegistryCenter(String serverAddr) {
        throw new UnsupportedOperationException(
                "NacosRegistryCenter is not fully implemented. "
                + "Please add nacos-client dependency and complete the implementation. "
                + "See class Javadoc for step-by-step instructions.");

        // >> Properties props = new Properties();
        // >> props.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        // >> this.namingService = NamingFactory.createNamingService(props);
        // >> log.info("[NacosRegistry] Connected: {}", serverAddr);
    }

    @Override
    public void register(ServiceMeta meta) {
        throw new UnsupportedOperationException("Add nacos-client dependency first");
        // >> Instance instance = new Instance();
        // >> instance.setIp(meta.getHost());
        // >> instance.setPort(meta.getPort());
        // >> instance.setWeight(meta.getWeight());
        // >> instance.setEphemeral(true);  // 临时实例，心跳超时自动下线
        // >> instance.setMetadata(Map.of("version", meta.getVersion(), "group", meta.getGroup()));
        // >> namingService.registerInstance(meta.getServiceKey(), instance);
        // >> log.info("[NacosRegistry] Registered: {} -> {}", meta.getServiceKey(), meta.getAddress());
    }

    @Override
    public void unregister(ServiceMeta meta) {
        throw new UnsupportedOperationException("Add nacos-client dependency first");
        // >> try {
        // >>     namingService.deregisterInstance(meta.getServiceKey(), meta.getHost(), meta.getPort());
        // >>     log.info("[NacosRegistry] Unregistered: {} -> {}", meta.getServiceKey(), meta.getAddress());
        // >> } catch (NacosException e) {
        // >>     log.warn("[NacosRegistry] Failed to unregister: {}", meta, e);
        // >> }
    }

    @Override
    public List<ServiceMeta> discover(String serviceKey) {
        throw new UnsupportedOperationException("Add nacos-client dependency first");
        // >> try {
        // >>     return namingService.getAllInstances(serviceKey).stream()
        // >>         .map(inst -> ServiceMeta.builder()
        // >>             .interfaceName(serviceKey)
        // >>             .host(inst.getIp())
        // >>             .port(inst.getPort())
        // >>             .weight((int) inst.getWeight())
        // >>             .version(inst.getMetadata().getOrDefault("version", ""))
        // >>             .group(inst.getMetadata().getOrDefault("group", ""))
        // >>             .build())
        // >>         .collect(Collectors.toList());
        // >> } catch (NacosException e) {
        // >>     throw new RuntimeException("Failed to discover from Nacos: " + serviceKey, e);
        // >> }
    }

    @Override
    public void subscribe(String serviceKey, ServiceChangeListener listener) {
        throw new UnsupportedOperationException("Add nacos-client dependency first");
        // >> EventListener nacosListener = event -> {
        // >>     if (event instanceof NamingEvent ne) {
        // >>         List<ServiceMeta> metas = ne.getInstances().stream()
        // >>             .map(inst -> ServiceMeta.builder()
        // >>                 .interfaceName(serviceKey)
        // >>                 .host(inst.getIp()).port(inst.getPort())
        // >>                 .weight((int) inst.getWeight())
        // >>                 .version(inst.getMetadata().getOrDefault("version", ""))
        // >>                 .group(inst.getMetadata().getOrDefault("group", ""))
        // >>                 .build())
        // >>             .collect(Collectors.toList());
        // >>         listener.onChanged(metas);
        // >>     }
        // >> };
        // >> subscriptions.computeIfAbsent(serviceKey, k -> new ConcurrentHashMap<>())
        // >>              .put(listener, nacosListener);
        // >> try {
        // >>     namingService.subscribe(serviceKey, nacosListener);
        // >> } catch (NacosException e) {
        // >>     throw new RuntimeException("Failed to subscribe to Nacos: " + serviceKey, e);
        // >> }
    }

    @Override
    public void unsubscribe(String serviceKey, ServiceChangeListener listener) {
        throw new UnsupportedOperationException("Add nacos-client dependency first");
        // >> Map<ServiceChangeListener, EventListener> map = subscriptions.get(serviceKey);
        // >> if (map != null) {
        // >>     EventListener nacosListener = map.remove(listener);
        // >>     if (nacosListener != null) {
        // >>         try { namingService.unsubscribe(serviceKey, nacosListener); }
        // >>         catch (NacosException e) { log.warn("[NacosRegistry] Unsubscribe error", e); }
        // >>     }
        // >> }
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Add nacos-client dependency first");
        // >> try { namingService.shutDown(); }
        // >> catch (NacosException e) { log.warn("[NacosRegistry] Shutdown error", e); }
    }
}
