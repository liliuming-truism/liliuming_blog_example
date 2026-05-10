package top.truism.blog.rpc;

import org.junit.jupiter.api.*;
import top.truism.blog.rpc.cluster.Invoker;
import top.truism.blog.rpc.filter.AccessLogFilter;
import top.truism.blog.rpc.filter.FilterChain;
import top.truism.blog.rpc.loadbalance.RoundRobinLoadBalance;
import top.truism.blog.rpc.proxy.RpcClientProxy;
import top.truism.blog.rpc.registry.*;
import top.truism.blog.rpc.transport.server.RpcServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注册中心抽象层测试
 *
 * <p>全部使用 {@link LocalRegistryCenter}（内存注册中心），无需外部依赖，
 * 涵盖：register / unregister / discover / subscribe / RegistryDirectory 动态更新。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistryCenterTest {

    private static final int PORT_A = 18891;
    private static final int PORT_B = 18892;

    private static RpcServer serverA;
    private static RpcServer serverB;

    @BeforeAll
    static void startServers() throws InterruptedException {
        serverA = new RpcServer(PORT_A)
                .register(HelloService.class, new HelloServiceImpl());
        serverB = new RpcServer(PORT_B)
                .register(HelloService.class, new HelloServiceImpl());

        startDaemon(serverA, "registry-test-server-A");
        startDaemon(serverB, "registry-test-server-B");
        Thread.sleep(500);
    }

    @AfterAll
    static void stopServers() {
        if (serverA != null) serverA.stop();
        if (serverB != null) serverB.stop();
    }

    // ================================================================
    // LocalRegistryCenter 单元测试（不涉及网络）
    // ================================================================

    @Test
    @Order(1)
    void testRegisterAndDiscover() {
        LocalRegistryCenter registry = new LocalRegistryCenter();

        ServiceMeta meta = ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1")
                .port(PORT_A)
                .build();
        registry.register(meta);

        List<ServiceMeta> found = registry.discover(HelloService.class.getName());
        assertEquals(1, found.size());
        assertEquals("127.0.0.1", found.get(0).getHost());
        assertEquals(PORT_A, found.get(0).getPort());
        assertEquals(100, found.get(0).getWeight(), "default weight should be 100");
    }

    @Test
    @Order(2)
    void testUnregister() {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        ServiceMeta meta = ServiceMeta.builder()
                .interfaceName("com.example.FooService")
                .host("127.0.0.1").port(9001).build();

        registry.register(meta);
        assertEquals(1, registry.discover("com.example.FooService").size());

        registry.unregister(meta);
        assertEquals(0, registry.discover("com.example.FooService").size());
    }

    @Test
    @Order(3)
    void testSubscribePushOnRegister() {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        String serviceKey = HelloService.class.getName();

        List<List<ServiceMeta>> notifications = new CopyOnWriteArrayList<>();
        registry.subscribe(serviceKey, notifications::add);

        // 注册第一个节点 → 触发通知
        registry.register(ServiceMeta.builder()
                .interfaceName(serviceKey).host("127.0.0.1").port(PORT_A).build());
        assertEquals(1, notifications.size());
        assertEquals(1, notifications.get(0).size());

        // 注册第二个节点 → 再次通知
        registry.register(ServiceMeta.builder()
                .interfaceName(serviceKey).host("127.0.0.1").port(PORT_B).build());
        assertEquals(2, notifications.size());
        assertEquals(2, notifications.get(1).size());
    }

    @Test
    @Order(4)
    void testSubscribePushOnUnregister() {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        String serviceKey = "com.example.BarService";

        ServiceMeta meta = ServiceMeta.builder()
                .interfaceName(serviceKey).host("127.0.0.1").port(9002).build();
        registry.register(meta);

        AtomicInteger notifyCount = new AtomicInteger();
        registry.subscribe(serviceKey, metas -> notifyCount.incrementAndGet());

        registry.unregister(meta);
        assertEquals(1, notifyCount.get(), "Unregister should trigger one notification");
    }

    @Test
    @Order(5)
    void testUnsubscribe() {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        String serviceKey = "com.example.BazService";

        AtomicInteger count = new AtomicInteger();
        ServiceChangeListener listener = metas -> count.incrementAndGet();

        registry.subscribe(serviceKey, listener);
        registry.register(ServiceMeta.builder()
                .interfaceName(serviceKey).host("127.0.0.1").port(9003).build());
        assertEquals(1, count.get());

        // 取消订阅后的变更不再通知
        registry.unsubscribe(serviceKey, listener);
        registry.register(ServiceMeta.builder()
                .interfaceName(serviceKey).host("127.0.0.1").port(9004).build());
        assertEquals(1, count.get(), "Should not receive notification after unsubscribe");
    }

    @Test
    @Order(6)
    void testServiceKeyWithVersionAndGroup() {
        LocalRegistryCenter registry = new LocalRegistryCenter();

        ServiceMeta v1 = ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .version("1.0").host("127.0.0.1").port(PORT_A).build();
        ServiceMeta v2 = ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .version("2.0").host("127.0.0.1").port(PORT_B).build();

        registry.register(v1);
        registry.register(v2);

        // 两个版本的 serviceKey 不同，互不干扰
        List<ServiceMeta> v1List = registry.discover(HelloService.class.getName() + ":1.0");
        List<ServiceMeta> v2List = registry.discover(HelloService.class.getName() + ":2.0");

        assertEquals(1, v1List.size());
        assertEquals(PORT_A, v1List.get(0).getPort());
        assertEquals(1, v2List.size());
        assertEquals(PORT_B, v2List.get(0).getPort());
    }

    @Test
    @Order(7)
    void testRegistryConfigFactory() {
        // LOCAL 类型工厂方法
        RegistryConfig cfg = RegistryConfig.local();
        assertEquals(RegistryConfig.Type.LOCAL, cfg.getType());

        RegistryCenter center = RegistryFactory.create(cfg);
        assertNotNull(center);
        assertInstanceOf(LocalRegistryCenter.class, center);
    }

    // ================================================================
    // RegistryDirectory 集成测试（涉及真实网络连接）
    // ================================================================

    @Test
    @Order(10)
    void testRegistryDirectoryInitialDiscovery() throws Exception {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        // 预先注册 server A
        registry.register(ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1").port(PORT_A).build());

        RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
        try {
            assertEquals(1, dir.getInvokers().size());
            assertEquals("127.0.0.1:" + PORT_A, dir.getInvokers().get(0).getAddress());
        } finally {
            dir.close();
        }
    }

    @Test
    @Order(11)
    void testRegistryDirectoryDynamicAddProvider() throws Exception {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        ServiceMeta metaA = ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1").port(PORT_A).build();
        registry.register(metaA);

        RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
        try {
            assertEquals(1, dir.getInvokers().size());

            // 动态增加 server B
            registry.register(ServiceMeta.builder()
                    .interfaceName(HelloService.class.getName())
                    .host("127.0.0.1").port(PORT_B).build());

            Thread.sleep(100); // 等待同步通知处理完成
            assertEquals(2, dir.getInvokers().size(), "Should have 2 invokers after adding server B");
        } finally {
            dir.close();
        }
    }

    @Test
    @Order(12)
    void testRegistryDirectoryDynamicRemoveProvider() throws Exception {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        ServiceMeta metaA = ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1").port(PORT_A).build();
        ServiceMeta metaB = ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1").port(PORT_B).build();

        registry.register(metaA);
        registry.register(metaB);

        RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
        try {
            assertEquals(2, dir.getInvokers().size());

            // 动态下线 server B
            registry.unregister(metaB);
            Thread.sleep(100);
            assertEquals(1, dir.getInvokers().size(), "Should have 1 invoker after removing server B");
            assertEquals("127.0.0.1:" + PORT_A, dir.getInvokers().get(0).getAddress());
        } finally {
            dir.close();
        }
    }

    @Test
    @Order(13)
    void testRegistryDirectoryWithRealRpc() throws Exception {
        LocalRegistryCenter registry = new LocalRegistryCenter();
        registry.register(ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1").port(PORT_A).build());
        registry.register(ServiceMeta.builder()
                .interfaceName(HelloService.class.getName())
                .host("127.0.0.1").port(PORT_B).build());

        RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
        try {
            List<Invoker> invokers = dir.getInvokers();
            assertEquals(2, invokers.size());

            // 通过 RegistryDirectory 获取的 Invoker 列表直接对接 RpcClientProxy
            HelloService svc = RpcClientProxy.builder()
                    .invokers(invokers)
                    .loadBalance(new RoundRobinLoadBalance())
                    .filterChain(FilterChain.of(new AccessLogFilter()))
                    .build()
                    .getProxy(HelloService.class);

            // 轮询两个节点各调用 3 次
            for (int i = 0; i < 6; i++) {
                assertEquals("Hello, registry-dir!", svc.sayHello("registry-dir"));
            }
        } finally {
            dir.close();
        }
    }

    @Test
    @Order(14)
    void testRpcServerWithRegistryCenter() throws InterruptedException {
        // 验证 RpcServer 在带注册中心的构造器下能正常发布服务
        LocalRegistryCenter registry = new LocalRegistryCenter();

        int port = 18893;
        RpcServer server = new RpcServer(port, "127.0.0.1", registry)
                .register(HelloService.class, new HelloServiceImpl());

        Thread serverThread = new Thread(() -> {
            try { server.start(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "registry-server");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(500);

        try {
            // 服务端自动把自身地址发布到 registry
            List<ServiceMeta> metas = registry.discover(HelloService.class.getName());
            assertEquals(1, metas.size(), "Server should auto-register to registry on start()");
            assertEquals("127.0.0.1", metas.get(0).getHost());
            assertEquals(port, metas.get(0).getPort());

            // 用发现的地址创建 Invoker 并发起真实调用
            RegistryDirectory dir = new RegistryDirectory(registry, HelloService.class.getName(), 5);
            HelloService svc = RpcClientProxy.builder()
                    .invokers(dir.getInvokers())
                    .build()
                    .getProxy(HelloService.class);

            assertEquals("Hello, auto-discovery!", svc.sayHello("auto-discovery"));
            dir.close();
        } finally {
            server.stop();
        }
    }

    // ---- 工具 ----

    private static void startDaemon(RpcServer server, String name) {
        Thread t = new Thread(() -> {
            try { server.start(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, name);
        t.setDaemon(true);
        t.start();
    }
}
