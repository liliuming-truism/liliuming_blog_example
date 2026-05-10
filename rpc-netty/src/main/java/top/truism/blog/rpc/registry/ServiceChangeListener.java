package top.truism.blog.rpc.registry;

import java.util.List;

/**
 * 服务变更监听器（对标 Dubbo {@code NotifyListener}）
 *
 * <p>注册中心检测到提供者列表发生变化时（上线/下线/权重变更），
 * 回调 {@link #onChanged(List)} 推送最新的完整服务列表。
 *
 * <p>配合 {@link RegistryCenter#subscribe} 使用：
 * <pre>{@code
 * registryCenter.subscribe("top.xxx.HelloService", metas -> {
 *     log.info("Providers updated: {}", metas);
 *     // 重建 Invoker 列表...
 * });
 * }</pre>
 *
 * @see RegistryCenter
 * @see top.truism.blog.rpc.registry.RegistryDirectory
 */
@FunctionalInterface
public interface ServiceChangeListener {

    /**
     * 服务提供者列表发生变化时调用
     *
     * @param metas 最新的完整提供者列表（非增量），可为空列表
     */
    void onChanged(List<ServiceMeta> metas);
}
