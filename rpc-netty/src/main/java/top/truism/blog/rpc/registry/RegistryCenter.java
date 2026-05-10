package top.truism.blog.rpc.registry;

import java.io.Closeable;
import java.util.List;

/**
 * 注册中心核心接口（对标 Dubbo {@code com.alibaba.dubbo.registry.Registry}）
 *
 * <p>聚合了服务的注册、注销、发现、订阅四个能力，是注册中心扩展的统一抽象：
 *
 * <pre>
 * ┌───────────────────────────────────────────────────────┐
 * │               RegistryCenter                          │
 * │  register()   unregister()   discover()   subscribe() │
 * └──────────┬───────────────────────────────────┬────────┘
 *            │                                   │
 *   LocalRegistryCenter           ZookeeperRegistryCenter
 *   （内存 ConcurrentHashMap）     （Curator + CuratorCache）
 *                                  NacosRegistryCenter（骨架）
 * </pre>
 *
 * <p><b>职责划分：</b>
 * <ul>
 *   <li>服务端调用 {@link #register} 发布自身地址，{@link #unregister} 下线时注销</li>
 *   <li>客户端调用 {@link #discover} 主动拉取，{@link #subscribe} 订阅推送变更</li>
 * </ul>
 *
 * <p><b>注意：</b>{@link ServiceRegistry}（本地 DI 容器）与本接口是两个不同层次：
 * {@code ServiceRegistry} 负责服务端反射调用的接口→实现映射（进程内），
 * {@code RegistryCenter} 负责跨进程的服务地址管理（网络注册中心）。
 */
public interface RegistryCenter extends Closeable {

    /**
     * 注册服务提供者
     *
     * <p>服务端启动后调用，将自身监听地址发布到注册中心。
     * ZooKeeper 实现使用临时节点（EPHEMERAL），会话断开后自动注销。
     *
     * @param meta 服务元数据（包含 host、port、version、group、weight）
     */
    void register(ServiceMeta meta);

    /**
     * 注销服务提供者
     *
     * <p>服务端优雅关闭时调用。ZooKeeper 实现中临时节点已在会话断开时
     * 自动删除，此方法仅作为主动提前注销使用。
     *
     * @param meta 待注销的服务元数据
     */
    void unregister(ServiceMeta meta);

    /**
     * 主动拉取当前可用的提供者列表（pull 模式）
     *
     * @param serviceKey 服务键，见 {@link ServiceMeta#getServiceKey()}
     * @return 当前存活提供者列表，可为空列表，不为 null
     */
    List<ServiceMeta> discover(String serviceKey);

    /**
     * 订阅服务变更（push 模式）
     *
     * <p>注册中心检测到 {@code serviceKey} 对应的提供者列表变化时，
     * 推送最新完整列表给 {@code listener}。同一 serviceKey 可注册多个 listener。
     *
     * @param serviceKey 服务键
     * @param listener   变更回调
     */
    void subscribe(String serviceKey, ServiceChangeListener listener);

    /**
     * 取消订阅
     *
     * @param serviceKey 服务键
     * @param listener   之前传入 {@link #subscribe} 的同一实例
     */
    void unsubscribe(String serviceKey, ServiceChangeListener listener);
}
