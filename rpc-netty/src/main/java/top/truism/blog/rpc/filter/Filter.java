package top.truism.blog.rpc.filter;

import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

/**
 * RPC 拦截器（对标 Dubbo {@code com.alibaba.dubbo.rpc.Filter}）
 *
 * <p>客户端和服务端共用同一接口，通过 {@link FilterChain} 构成有序拦截链：
 * <pre>
 *   Filter1 → Filter2 → ... → 终态调用（反射 / 网络请求）
 * </pre>
 *
 * <p>实现时 <strong>必须</strong> 调用 {@code next.proceed(request)} 将请求传递下去，
 * 否则链路会在当前节点断开。
 *
 * <p>示例：
 * <pre>{@code
 * public class MyFilter implements Filter {
 *     public RpcResponse invoke(RpcRequest request, Invocation next) throws Exception {
 *         // 前置处理
 *         RpcResponse response = next.proceed(request);
 *         // 后置处理
 *         return response;
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface Filter {

    RpcResponse invoke(RpcRequest request, Invocation next) throws Exception;
}
