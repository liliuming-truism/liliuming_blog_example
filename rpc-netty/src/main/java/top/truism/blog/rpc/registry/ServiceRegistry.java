package top.truism.blog.rpc.registry;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地服务注册表
 *
 * <p>注册键格式：{@code interfaceName} 或 {@code interfaceName:version}（有版本时）。
 * 查找时先按精确键匹配，若未命中且 version 非空则 fallback 到无版本键。
 */
@Slf4j
public class ServiceRegistry {

    private final Map<String, Object> services = new ConcurrentHashMap<>();

    /**
     * 注册服务实现（无版本）
     */
    public void register(String interfaceName, Object impl) {
        register(interfaceName, "", impl);
    }

    /**
     * 注册服务实现（带版本）
     *
     * @param interfaceName 接口全限定名
     * @param version       服务版本，空字符串表示无版本
     * @param impl          接口实现对象
     */
    public void register(String interfaceName, String version, Object impl) {
        String key = buildKey(interfaceName, version);
        services.put(key, impl);
        log.info("Service registered: {} -> {}", key, impl.getClass().getName());
    }

    /**
     * 查找服务实现（无版本）
     */
    public Object lookup(String interfaceName) {
        return lookup(interfaceName, "");
    }

    /**
     * 查找服务实现（带版本）
     *
     * <p>先按 {@code interfaceName:version} 精确查找；若未命中且 version 非空，
     * 再 fallback 到无版本键。
     */
    public Object lookup(String interfaceName, String version) {
        String key = buildKey(interfaceName, version);
        Object impl = services.get(key);

        // fallback：有版本但未注册时，尝试无版本实现
        if (impl == null && version != null && !version.isEmpty()) {
            impl = services.get(interfaceName);
        }

        if (impl == null) {
            log.error("No service for key={}, registered={}", key, services.keySet());
            throw new IllegalStateException("No service registered for: " + key);
        }
        log.debug("Service lookup: {} -> {}", key, impl.getClass().getName());
        return impl;
    }

    /**
     * 返回所有已注册的键（供 ZK 发布时遍历）
     */
    public java.util.Set<String> getRegisteredNames() {
        return java.util.Collections.unmodifiableSet(services.keySet());
    }

    private static String buildKey(String interfaceName, String version) {
        return (version == null || version.isEmpty()) ? interfaceName : interfaceName + ":" + version;
    }
}
