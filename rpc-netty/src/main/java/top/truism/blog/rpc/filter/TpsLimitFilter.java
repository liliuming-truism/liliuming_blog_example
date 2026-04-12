package top.truism.blog.rpc.filter;

import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TPS 限流过滤器（对标 Dubbo {@code TpsLimitFilter}）
 *
 * <p>基于 1 秒滑动窗口，对每个 {@code interfaceName#methodName} 独立计数。
 * 超过 {@code maxTps} 后直接返回失败响应，不调用后续链。
 *
 * <p>通常挂载在服务端，防止单一方法被过量调用。
 *
 * <p>示例：
 * <pre>{@code
 * new RpcServer(8888)
 *     .addFilter(new TpsLimitFilter(100))   // 每个方法每秒最多 100 次
 *     .register(...)
 *     .start();
 * }</pre>
 */
@Slf4j
public class TpsLimitFilter implements Filter {

    private final int maxTps;

    /**
     * key   = interfaceName#methodName
     * value = [windowSecond, counter]
     */
    private final ConcurrentHashMap<String, long[]> state = new ConcurrentHashMap<>();

    public TpsLimitFilter(int maxTps) {
        if (maxTps <= 0) throw new IllegalArgumentException("maxTps must be positive");
        this.maxTps = maxTps;
    }

    @Override
    public RpcResponse invoke(RpcRequest request, Invocation next) throws Exception {
        String key = request.getInterfaceName() + "#" + request.getMethodName();
        long nowSec = System.currentTimeMillis() / 1000;

        // state[0] = 当前窗口秒数，state[1] = 当前计数（用 synchronized 保证原子性）
        long[] slot = state.computeIfAbsent(key, k -> new long[]{nowSec, 0});
        long count;
        synchronized (slot) {
            if (slot[0] != nowSec) {
                slot[0] = nowSec;
                slot[1] = 0;
            }
            count = ++slot[1];
        }

        if (count > maxTps) {
            log.warn("[TpsLimit] {} exceeded limit={} current={}", key, maxTps, count);
            return RpcResponse.fail("TPS limit exceeded for " + key + " (limit=" + maxTps + "/s)");
        }
        return next.proceed(request);
    }
}
