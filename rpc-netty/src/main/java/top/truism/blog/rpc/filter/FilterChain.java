package top.truism.blog.rpc.filter;

import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 不可变拦截器链
 *
 * <p>构建后链结构固定，线程安全，可被多线程复用。
 *
 * <p>使用示例：
 * <pre>{@code
 * FilterChain chain = FilterChain.of(
 *     new AccessLogFilter(),
 *     new TpsLimitFilter(100)
 * );
 * RpcResponse resp = chain.execute(request, terminalInvocation);
 * }</pre>
 */
public class FilterChain {

    /** 空链（无任何过滤器，直接执行终态调用） */
    public static final FilterChain EMPTY = new FilterChain(Collections.emptyList());

    private final List<Filter> filters;

    public FilterChain(List<Filter> filters) {
        this.filters = List.copyOf(filters);
    }

    public static FilterChain of(Filter... filters) {
        return new FilterChain(Arrays.asList(filters));
    }

    /**
     * 执行拦截链
     *
     * @param request  RPC 请求
     * @param terminal 链尾的终态调用（服务端反射 / 客户端网络）
     */
    public RpcResponse execute(RpcRequest request, Invocation terminal) throws Exception {
        return buildChain(0, terminal).proceed(request);
    }

    /** 递归构建链：index 处的 Filter 包裹后续链 */
    private Invocation buildChain(int index, Invocation terminal) {
        if (index >= filters.size()) {
            return terminal;
        }
        Filter current = filters.get(index);
        Invocation next = buildChain(index + 1, terminal);
        return req -> current.invoke(req, next);
    }
}
