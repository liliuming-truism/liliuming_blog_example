package top.truism.blog.rpc.registry;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存注册中心
 *
 * <p>使用 {@link ConcurrentHashMap} 存储服务元数据，无需任何外部依赖。
 * 适合单机开发、集成测试和多服务器模拟场景。
 *
 * <p>{@link #register} / {@link #unregister} 操作后同步通知所有订阅者，
 * 订阅者在调用方线程中执行（非异步）。
 *
 * <p>生命周期：{@link #close()} 清空所有数据和订阅，通常不需要显式调用。
 */
@Slf4j
public class LocalRegistryCenter implements RegistryCenter {

    /** key = serviceKey，value = 当前存活的提供者列表 */
    private final ConcurrentHashMap<String, List<ServiceMeta>> registry = new ConcurrentHashMap<>();

    /** key = serviceKey，value = 监听器列表 */
    private final ConcurrentHashMap<String, List<ServiceChangeListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void register(ServiceMeta meta) {
        String key = meta.getServiceKey();
        registry.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(meta);
        log.info("[LocalRegistry] Registered: {} -> {}", key, meta.getAddress());
        notifyListeners(key);
    }

    @Override
    public void unregister(ServiceMeta meta) {
        String key = meta.getServiceKey();
        List<ServiceMeta> metas = registry.get(key);
        if (metas != null) {
            boolean removed = metas.removeIf(
                    m -> m.getHost().equals(meta.getHost()) && m.getPort() == meta.getPort());
            if (removed) {
                log.info("[LocalRegistry] Unregistered: {} -> {}", key, meta.getAddress());
                notifyListeners(key);
            }
        }
    }

    @Override
    public List<ServiceMeta> discover(String serviceKey) {
        List<ServiceMeta> metas = registry.get(serviceKey);
        return metas == null ? Collections.emptyList() : Collections.unmodifiableList(metas);
    }

    @Override
    public void subscribe(String serviceKey, ServiceChangeListener listener) {
        listeners.computeIfAbsent(serviceKey, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("[LocalRegistry] Subscribed: {}", serviceKey);
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
        registry.clear();
        listeners.clear();
        log.info("[LocalRegistry] Closed");
    }

    private void notifyListeners(String serviceKey) {
        List<ServiceChangeListener> ls = listeners.get(serviceKey);
        if (ls == null || ls.isEmpty()) return;
        List<ServiceMeta> snapshot = discover(serviceKey);
        ls.forEach(l -> {
            try {
                l.onChanged(snapshot);
            } catch (Exception e) {
                log.error("[LocalRegistry] Listener error for {}", serviceKey, e);
            }
        });
    }
}
