package top.truism.blog.rpc.loadbalance;

import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.protocol.RpcRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡（对标 Dubbo {@code ConsistentHashLoadBalance}）
 *
 * <p>将节点（及虚拟节点）映射到哈希环上，每次请求根据调用参数的哈希值
 * 顺时针找到最近的节点。相同参数的请求总是路由到同一节点，适合有状态服务
 * 或需要缓存亲和（cache affinity）的场景。
 *
 * <p>实现要点：
 * <ul>
 *   <li>每个真实节点对应 {@value #VIRTUAL_NODES} 个虚拟节点，减少数据倾斜</li>
 *   <li>使用 MD5 生成 128 bit 哈希，每 4 字节取一段，一次 MD5 产生 4 个虚拟节点</li>
 *   <li>哈希键 = {@code methodName + first_arg}（与 Dubbo 默认行为一致）</li>
 *   <li>节点列表变化时自动重建环（以节点地址集合的字符串作为缓存键）</li>
 * </ul>
 */
public class ConsistentHashLoadBalance implements LoadBalance {

    /** 每个真实节点对应的虚拟节点数量 */
    private static final int VIRTUAL_NODES = 160;

    /** key = 节点地址集合字符串，避免节点变化后使用旧环 */
    private final ConcurrentHashMap<String, TreeMap<Long, Invoker>> ringCache = new ConcurrentHashMap<>();

    @Override
    public Invoker select(List<Invoker> invokers, RpcRequest request) {
        String cacheKey = buildCacheKey(invokers);
        TreeMap<Long, Invoker> ring = ringCache.computeIfAbsent(cacheKey, k -> buildRing(invokers));

        // 用方法名 + 首个参数作为路由键（相同入参 → 同一节点）
        String routeKey = buildRouteKey(request);
        long hash = hash(routeKey, 0);

        Map.Entry<Long, Invoker> entry = ring.ceilingEntry(hash);
        // 超出环尾则绕回环首
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    /** 构建哈希环：每个节点生成 VIRTUAL_NODES 个虚拟节点 */
    private TreeMap<Long, Invoker> buildRing(List<Invoker> invokers) {
        TreeMap<Long, Invoker> ring = new TreeMap<>();
        for (Invoker invoker : invokers) {
            // 每次 MD5 产生 4 个哈希段，循环 VIRTUAL_NODES/4 次
            for (int i = 0; i < VIRTUAL_NODES / 4; i++) {
                byte[] digest = md5(invoker.getAddress() + "#" + i);
                for (int j = 0; j < 4; j++) {
                    ring.put(hash(digest, j), invoker);
                }
            }
        }
        return ring;
    }

    private String buildCacheKey(List<Invoker> invokers) {
        List<String> addresses = invokers.stream()
                .map(Invoker::getAddress)
                .sorted()
                .toList();
        return String.join(",", addresses);
    }

    private String buildRouteKey(RpcRequest request) {
        String firstArg = "";
        if (request.getParameters() != null && request.getParameters().length > 0
                && request.getParameters()[0] != null) {
            firstArg = String.valueOf(request.getParameters()[0]);
        }
        return request.getMethodName() + firstArg;
    }

    /** 从预计算的 MD5 摘要中取第 index 段（每段 4 字节）构成 long 哈希 */
    private long hash(byte[] digest, int index) {
        int base = index * 4;
        return ((long) (digest[base + 3] & 0xFF) << 24)
                | ((long) (digest[base + 2] & 0xFF) << 16)
                | ((long) (digest[base + 1] & 0xFF) << 8)
                | ((long) (digest[base]     & 0xFF));
    }

    /** 对字符串计算哈希（先 MD5，再取第 0 段） */
    private long hash(String key, int index) {
        return hash(md5(key), index);
    }

    private byte[] md5(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
