package top.truism.blog.rpc.filter;

import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

/**
 * 调用链中下一个节点的抽象
 *
 * <p>每个 {@link Filter} 持有指向下一节点的 {@code Invocation}，形成调用链。
 * 链尾的 {@code Invocation} 是真正执行业务逻辑（服务端反射调用 / 客户端网络请求）的终态节点。
 *
 * @see Filter
 * @see FilterChain
 */
@FunctionalInterface
public interface Invocation {

    RpcResponse proceed(RpcRequest request) throws Exception;
}
