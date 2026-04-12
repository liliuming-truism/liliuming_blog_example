package top.truism.blog.rpc.filter;

import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

/**
 * 访问日志过滤器（对标 Dubbo {@code AccessLogFilter}）
 *
 * <p>记录每次 RPC 调用的接口、方法、耗时和状态，便于监控与问题排查。
 * 客户端和服务端均可挂载。
 *
 * <p>日志格式：
 * <pre>
 *   [AccessLog] top.xxx.HelloService#sayHello() status=0 elapsed=3ms
 * </pre>
 */
@Slf4j
public class AccessLogFilter implements Filter {

    @Override
    public RpcResponse invoke(RpcRequest request, Invocation next) throws Exception {
        long start = System.currentTimeMillis();
        String method = request.getInterfaceName() + "#" + request.getMethodName() + "()";
        try {
            RpcResponse response = next.proceed(request);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[AccessLog] {} status={} elapsed={}ms", method, response.getStatus(), elapsed);
            return response;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[AccessLog] {} EXCEPTION elapsed={}ms msg={}", method, elapsed, e.getMessage());
            throw e;
        }
    }
}
