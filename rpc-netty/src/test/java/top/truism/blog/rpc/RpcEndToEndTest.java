package top.truism.blog.rpc;

import org.junit.jupiter.api.*;
import top.truism.blog.rpc.cluster.*;
import top.truism.blog.rpc.codec.CompressType;
import top.truism.blog.rpc.filter.*;
import top.truism.blog.rpc.loadbalance.*;
import top.truism.blog.rpc.protocol.SerializationType;
import top.truism.blog.rpc.proxy.RpcClientProxy;
import top.truism.blog.rpc.transport.client.RpcClient;
import top.truism.blog.rpc.transport.server.RpcServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试：覆盖基础 RPC、序列化、压缩、Filter 链、LoadBalance、Cluster 容错
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RpcEndToEndTest {

    private static final int PORT_A = 18881;
    private static final int PORT_B = 18882;

    private static RpcServer serverA;
    private static RpcServer serverB;
    private static RpcClient clientA;
    private static RpcClient clientB;

    @BeforeAll
    static void startServers() throws InterruptedException {
        // 服务端 A：挂载 AccessLog + TPS 限流（20/s）
        serverA = new RpcServer(PORT_A)
                .addFilter(new AccessLogFilter())
                .addFilter(new TpsLimitFilter(20))
                .register(HelloService.class, new HelloServiceImpl());

        // 服务端 B：仅 AccessLog
        serverB = new RpcServer(PORT_B)
                .addFilter(new AccessLogFilter())
                .register(HelloService.class, new HelloServiceImpl());

        startServerDaemon(serverA, "rpc-server-A");
        startServerDaemon(serverB, "rpc-server-B");

        Thread.sleep(500);

        clientA = new RpcClient("127.0.0.1", PORT_A);
        clientA.connect();
        clientB = new RpcClient("127.0.0.1", PORT_B);
        clientB.connect();
    }

    @AfterAll
    static void tearDown() {
        if (clientA != null) clientA.shutdown();
        if (clientB != null) clientB.shutdown();
        if (serverA != null) serverA.stop();
        if (serverB != null) serverB.stop();
    }

    // ================================================================
    // 基础功能
    // ================================================================

    @Test
    @Order(1)
    void testSayHello() {
        HelloService svc = new RpcClientProxy(clientA).getProxy(HelloService.class);
        assertEquals("Hello, RPC!", svc.sayHello("RPC"));
    }

    @Test
    @Order(2)
    void testAdd() {
        HelloService svc = new RpcClientProxy(clientA).getProxy(HelloService.class);
        assertEquals(10, svc.add(3, 7));
    }

    @Test
    @Order(3)
    void testConcurrentCalls() throws InterruptedException {
        HelloService svc = new RpcClientProxy(clientA).getProxy(HelloService.class);
        int n = 10;
        int[] results = new int[n];
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            threads[idx] = new Thread(() -> results[idx] = svc.add(idx, idx));
            threads[idx].start();
        }
        for (Thread t : threads) t.join();
        for (int i = 0; i < n; i++) {
            assertEquals(i + i, results[i], "Concurrent call mismatch at index " + i);
        }
    }

    // ================================================================
    // JSON 序列化
    // ================================================================

    @Test
    @Order(10)
    void testJsonSerialization() {
        // 通过 Builder 指定 JSON 序列化
        Invoker invoker = new ConfigurableInvoker(clientA, SerializationType.JSON, (byte) 0);
        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(invoker))
                .build()
                .getProxy(HelloService.class);

        assertEquals("Hello, JSON!", svc.sayHello("JSON"));
        assertEquals(15, svc.add(7, 8));
    }

    // ================================================================
    // Gzip 压缩
    // ================================================================

    @Test
    @Order(11)
    void testGzipCompression() {
        Invoker invoker = new ConfigurableInvoker(clientA, SerializationType.JDK,
                CompressType.GZIP.getCode());
        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(invoker))
                .build()
                .getProxy(HelloService.class);

        assertEquals("Hello, Gzip!", svc.sayHello("Gzip"));
    }

    // ================================================================
    // Filter 链
    // ================================================================

    @Test
    @Order(20)
    void testClientSideAccessLogFilter() {
        // 客户端挂载 AccessLog，仅验证调用正常不报错
        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(new NettyInvoker(clientA, 5)))
                .filterChain(FilterChain.of(new AccessLogFilter()))
                .build()
                .getProxy(HelloService.class);

        assertEquals("Hello, Filter!", svc.sayHello("Filter"));
    }

    @Test
    @Order(21)
    void testTpsLimitFilter() {
        // 服务端 A 配置了 TPS=20/s，快速发送 25 次应有至少 1 次被限流
        HelloService svc = new RpcClientProxy(clientA).getProxy(HelloService.class);

        int blocked = 0;
        for (int i = 0; i < 25; i++) {
            try {
                svc.sayHello("tps-" + i);
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("TPS limit")) {
                    blocked++;
                }
            }
        }
        assertTrue(blocked >= 1, "Expected at least 1 request to be TPS-limited, got " + blocked);
    }

    @Test
    @Order(22)
    void testCustomFilter() {
        // 自定义过滤器：统计调用次数（使用 server B，无 TPS 限制）
        AtomicInteger counter = new AtomicInteger();
        Filter countingFilter = (req, next) -> {
            counter.incrementAndGet();
            return next.proceed(req);
        };

        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(new NettyInvoker(clientB, 5)))
                .filterChain(FilterChain.of(countingFilter))
                .build()
                .getProxy(HelloService.class);

        svc.sayHello("a");
        svc.sayHello("b");
        svc.add(1, 2);

        assertEquals(3, counter.get());
    }

    // ================================================================
    // 负载均衡
    // ================================================================

    @Test
    @Order(30)
    void testRoundRobinLoadBalance() throws InterruptedException {
        Invoker invA = new NettyInvoker(clientA, 5);
        Invoker invB = new NettyInvoker(clientB, 5);

        // 记录每个节点地址被命中次数
        Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        hits.put(invA.getAddress(), new AtomicInteger());
        hits.put(invB.getAddress(), new AtomicInteger());

        Filter tracker = (req, next) -> {
            // 在过滤器层无法直接感知节点地址，借助自定义 invoker 包装统计
            return next.proceed(req);
        };

        // 直接使用 RoundRobinLoadBalance 验证分发结果
        RoundRobinLoadBalance lb = new RoundRobinLoadBalance();
        List<Invoker> invokers = List.of(invA, invB);
        int total = 10;
        for (int i = 0; i < total; i++) {
            Invoker selected = lb.select(invokers, null);
            hits.get(selected.getAddress()).incrementAndGet();
        }

        // 轮询结果：A 和 B 各 5 次
        assertEquals(5, hits.get(invA.getAddress()).get());
        assertEquals(5, hits.get(invB.getAddress()).get());
    }

    @Test
    @Order(31)
    void testLeastActiveLoadBalance() {
        Invoker fast = new NettyInvoker(clientA, 5);  // activeCount = 0
        Invoker busy = new NettyInvoker(clientB, 5);  // 模拟 activeCount > 0（暂无在途请求）

        LeastActiveLoadBalance lb = new LeastActiveLoadBalance();
        // 两节点都空闲时，随机选一个（activeCount=0）
        Invoker selected = lb.select(List.of(fast, busy), null);
        assertNotNull(selected);
    }

    @Test
    @Order(32)
    void testConsistentHashLoadBalance() {
        Invoker invA = new NettyInvoker(clientA, 5);
        Invoker invB = new NettyInvoker(clientB, 5);
        List<Invoker> invokers = List.of(invA, invB);

        ConsistentHashLoadBalance lb = new ConsistentHashLoadBalance();

        // 相同方法名 + 相同首参 → 总是路由到同一节点
        top.truism.blog.rpc.protocol.RpcRequest req = top.truism.blog.rpc.protocol.RpcRequest.builder()
                .interfaceName("top.truism.blog.rpc.HelloService")
                .methodName("sayHello")
                .parameterTypes(new Class[]{String.class})
                .parameters(new Object[]{"sticky-key"})
                .build();

        Invoker first = lb.select(invokers, req);
        for (int i = 0; i < 20; i++) {
            assertSame(first, lb.select(invokers, req), "ConsistentHash should route to same node");
        }
    }

    // ================================================================
    // Cluster 容错
    // ================================================================

    @Test
    @Order(40)
    void testFailoverCluster() {
        // 构造一个必然失败的 Invoker + 一个正常的 NettyInvoker
        Invoker failing = new AlwaysFailInvoker("127.0.0.1:99999");
        Invoker normal  = new NettyInvoker(clientB, 5);

        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(failing, normal))
                // 强制先选 failing，再 fallback 到 normal
                .loadBalance(new SequentialLoadBalance())
                .cluster(new FailoverClusterInvoker(1))
                .build()
                .getProxy(HelloService.class);

        // 第一次调用 failing 节点失败后应自动重试 normal 节点
        assertEquals("Hello, Failover!", svc.sayHello("Failover"));
    }

    @Test
    @Order(41)
    void testFailfastCluster() {
        Invoker failing = new AlwaysFailInvoker("127.0.0.1:99999");

        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(failing))
                .cluster(new FailfastClusterInvoker())
                .build()
                .getProxy(HelloService.class);

        assertThrows(RuntimeException.class, () -> svc.sayHello("x"));
    }

    @Test
    @Order(42)
    void testMultiServerProxy() throws InterruptedException {
        // 两个真实节点 + RoundRobin + Failover（等待 TPS 窗口重置后再访问 server A）
        Thread.sleep(1100);

        Invoker invA = new NettyInvoker(clientA, 5);
        Invoker invB = new NettyInvoker(clientB, 5);

        HelloService svc = RpcClientProxy.builder()
                .invokers(List.of(invA, invB))
                .loadBalance(new RoundRobinLoadBalance())
                .cluster(new FailoverClusterInvoker(1))
                .filterChain(FilterChain.of(new AccessLogFilter()))
                .build()
                .getProxy(HelloService.class);

        for (int i = 0; i < 6; i++) {
            assertEquals("Hello, multi!", svc.sayHello("multi"));
        }
    }

    // ================================================================
    // 测试辅助类
    // ================================================================

    /** 支持自定义序列化类型和压缩类型的 Invoker */
    static class ConfigurableInvoker implements Invoker {
        private final RpcClient client;
        private final SerializationType serType;
        private final byte compress;

        ConfigurableInvoker(RpcClient client, SerializationType serType, byte compress) {
            this.client = client;
            this.serType = serType;
            this.compress = compress;
        }

        @Override
        public top.truism.blog.rpc.protocol.RpcResponse invoke(
                top.truism.blog.rpc.protocol.RpcRequest request) throws Exception {
            top.truism.blog.rpc.protocol.RpcMessage msg =
                    top.truism.blog.rpc.protocol.RpcMessage.builder()
                            .messageType(top.truism.blog.rpc.protocol.MessageType.REQUEST)
                            .serializationType(serType)
                            .compress(compress)
                            .status((byte) 0)
                            .requestId(System.nanoTime())
                            .body(request)
                            .build();
            // 直接发 RpcMessage（绕过 RpcClient 封装），从响应中取出 RpcResponse body
            top.truism.blog.rpc.protocol.RpcMessage resp =
                    client.sendMessage(msg).get(5, java.util.concurrent.TimeUnit.SECONDS);
            return (top.truism.blog.rpc.protocol.RpcResponse) resp.getBody();
        }

        @Override public String getAddress()  { return client.getAddress(); }
        @Override public boolean isAvailable() { return client.isConnected(); }
        @Override public int getActiveCount()  { return 0; }
    }

    /** 总是抛出异常的 Invoker，用于测试 Failover */
    static class AlwaysFailInvoker implements Invoker {
        private final String address;
        AlwaysFailInvoker(String address) { this.address = address; }

        @Override
        public top.truism.blog.rpc.protocol.RpcResponse invoke(
                top.truism.blog.rpc.protocol.RpcRequest request) throws Exception {
            throw new RuntimeException("Simulated network failure from " + address);
        }

        @Override public String getAddress()   { return address; }
        @Override public boolean isAvailable() { return false; }
        @Override public int getActiveCount()  { return 0; }
    }

    /** 按顺序返回节点（先失败节点，再正常节点），用于测试 Failover */
    static class SequentialLoadBalance implements LoadBalance {
        private final AtomicInteger idx = new AtomicInteger(0);

        @Override
        public Invoker select(List<Invoker> invokers, top.truism.blog.rpc.protocol.RpcRequest request) {
            int i = idx.getAndIncrement();
            return invokers.get(i % invokers.size());
        }
    }

    // ---- 工具 ----

    private static void startServerDaemon(RpcServer server, String name) {
        Thread t = new Thread(() -> {
            try { server.start(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, name);
        t.setDaemon(true);
        t.start();
    }
}
