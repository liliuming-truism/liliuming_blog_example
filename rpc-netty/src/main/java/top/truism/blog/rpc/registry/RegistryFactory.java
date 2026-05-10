package top.truism.blog.rpc.registry;

/**
 * 注册中心工厂（对标 Dubbo {@code RegistryFactory} SPI）
 *
 * <p>根据 {@link RegistryConfig} 创建对应的 {@link RegistryCenter} 实例：
 * <pre>{@code
 * RegistryCenter registry = RegistryFactory.create(RegistryConfig.zookeeper("127.0.0.1:2181"));
 * }</pre>
 *
 * <p>Dubbo 通过 SPI 实现扩展（{@code @SPI("zookeeper")}），此处简化为 switch 工厂，
 * 原理相同：根据 type 标识路由到对应实现。
 */
public class RegistryFactory {

    private RegistryFactory() {}

    /**
     * 根据配置创建 RegistryCenter 实例
     *
     * @param config 注册中心配置
     * @return 对应的 RegistryCenter 实现
     * @throws IllegalArgumentException 若 type 未知
     */
    public static RegistryCenter create(RegistryConfig config) {
        return switch (config.getType()) {
            case LOCAL     -> new LocalRegistryCenter();
            case ZOOKEEPER -> new ZookeeperRegistryCenter(config.getAddress());
            case NACOS     -> new NacosRegistryCenter(config.getAddress());
        };
    }
}
