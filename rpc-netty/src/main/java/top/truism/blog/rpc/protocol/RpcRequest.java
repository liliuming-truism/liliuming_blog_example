package top.truism.blog.rpc.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {

    /** 调用的接口全限定名 */
    private String interfaceName;

    /** 调用的方法名 */
    private String methodName;

    /** 方法参数类型列表 */
    private Class<?>[] parameterTypes;

    /** 方法实参列表 */
    private Object[] parameters;

    /** 服务版本，用于同一接口多版本共存 */
    private String version;

    /** 服务分组，用于同一接口多实现隔离（如：provider-A / provider-B） */
    private String group;
}
