package top.truism.blog.rpc.registry;

import lombok.Builder;
import lombok.Data;

/**
 * 注册中心配置（对标 Dubbo registry URL 参数）
 *
 * <p>使用静态工厂方法快速创建：
 * <pre>{@code
 * RegistryConfig local = RegistryConfig.local();
 * RegistryConfig zk    = RegistryConfig.zookeeper("127.0.0.1:2181");
 * RegistryConfig nacos = RegistryConfig.nacos("127.0.0.1:8848");
 * }</pre>
 *
 * @see RegistryFactory
 */
@Data
@Builder
public class RegistryConfig {

    public enum Type {
        /** 内存注册中心，无需外部服务，适合单机开发/测试 */
        LOCAL,
        /** ZooKeeper 注册中心，需 Apache ZooKeeper 服务 */
        ZOOKEEPER,
        /**
         * Nacos 注册中心，需 Alibaba Nacos 服务。
         * <p><b>启用前</b>请在 pom.xml 中添加 nacos-client 依赖，
         * 并完善 {@link NacosRegistryCenter} 中的注释代码。
         */
        NACOS
    }

    /** 注册中心类型 */
    @Builder.Default
    private Type type = Type.LOCAL;

    /**
     * 注册中心地址
     * <ul>
     *   <li>ZooKeeper：{@code "127.0.0.1:2181"}，多节点逗号分隔</li>
     *   <li>Nacos：{@code "127.0.0.1:8848"}</li>
     *   <li>LOCAL：忽略</li>
     * </ul>
     */
    private String address;

    // ---- 静态工厂 ----

    public static RegistryConfig local() {
        return RegistryConfig.builder().type(Type.LOCAL).build();
    }

    public static RegistryConfig zookeeper(String connectString) {
        return RegistryConfig.builder().type(Type.ZOOKEEPER).address(connectString).build();
    }

    public static RegistryConfig nacos(String serverAddr) {
        return RegistryConfig.builder().type(Type.NACOS).address(serverAddr).build();
    }
}
