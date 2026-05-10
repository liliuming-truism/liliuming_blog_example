package top.truism.blog.rpc.registry;

import lombok.Builder;
import lombok.Data;

/**
 * 服务元数据（对标 Dubbo URL）
 *
 * <p>描述一个服务提供者节点的完整信息：
 * <ul>
 *   <li>{@code interfaceName} — 接口全限定名</li>
 *   <li>{@code version}  — 接口版本，空串表示无版本</li>
 *   <li>{@code group}    — 服务分组，用于多实现隔离</li>
 *   <li>{@code host}     — 提供者 IP/域名</li>
 *   <li>{@code port}     — 提供者监听端口</li>
 *   <li>{@code weight}   — 节点权重，默认 100，供加权负载均衡使用</li>
 * </ul>
 *
 * <p>服务键（serviceKey）用于注册中心的路径或 Map 键：
 * <ul>
 *   <li>无版本无分组：{@code top.xxx.HelloService}</li>
 *   <li>有版本：{@code top.xxx.HelloService:1.0}</li>
 *   <li>有版本+分组：{@code top.xxx.HelloService:1.0/groupA}</li>
 * </ul>
 */
@Data
@Builder
public class ServiceMeta {

    /** 接口全限定名 */
    private String interfaceName;

    /** 服务版本，空串表示无版本 */
    @Builder.Default
    private String version = "";

    /** 服务分组，空串表示无分组 */
    @Builder.Default
    private String group = "";

    /** 提供者 IP 或主机名 */
    private String host;

    /** 提供者监听端口 */
    private int port;

    /**
     * 节点权重（1–100），默认 100。
     * 供加权随机/加权轮询负载均衡使用（当前版本预留，尚未接入 LoadBalance）。
     */
    @Builder.Default
    private int weight = 100;

    // ---- 派生属性 ----

    /**
     * 服务键：注册中心中唯一标识一个服务（不含地址信息）。
     * <p>ZK 路径：{@code /rpc/{serviceKey}/providers/{host:port}}
     */
    public String getServiceKey() {
        String key = interfaceName;
        if (version != null && !version.isEmpty()) {
            key += ":" + version;
        }
        if (group != null && !group.isEmpty()) {
            key += "/" + group;
        }
        return key;
    }

    /** 节点地址，格式 {@code host:port} */
    public String getAddress() {
        return host + ":" + port;
    }
}
