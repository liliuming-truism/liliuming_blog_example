package top.truism.blog.rpc.transport.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.codec.RpcMessageCodec;
import top.truism.blog.rpc.filter.Filter;
import top.truism.blog.rpc.filter.FilterChain;
import top.truism.blog.rpc.registry.RegistryCenter;
import top.truism.blog.rpc.registry.ServiceMeta;
import top.truism.blog.rpc.registry.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RPC 服务端
 *
 * <p><b>单机模式（无注册中心）：</b>
 * <pre>{@code
 * RpcServer server = new RpcServer(8888);
 * server.addFilter(new AccessLogFilter())
 *       .register(HelloService.class, new HelloServiceImpl())
 *       .start();
 * }</pre>
 *
 * <p><b>集群模式（配合注册中心）：</b>
 * <pre>{@code
 * RegistryCenter registry = RegistryFactory.create(RegistryConfig.zookeeper("127.0.0.1:2181"));
 * RpcServer server = new RpcServer(8888, "192.168.1.100", registry);
 * server.register(HelloService.class, new HelloServiceImpl())
 *       .start();   // 绑定端口后自动向注册中心发布所有服务
 * }</pre>
 *
 * <p><b>优雅关闭：</b>
 * <pre>{@code
 * server.stop();    // 注销服务 → 关闭监听 → 释放线程池
 * }</pre>
 */
@Slf4j
public class RpcServer {

    private static final int MAX_FRAME_LENGTH  = 16 * 1024 * 1024;
    private static final int LENGTH_FIELD_OFFSET = 15;
    private static final int LENGTH_FIELD_LENGTH = 4;

    private final int port;

    /**
     * 本机对外暴露的 IP/主机名，写入注册中心供消费者连接。
     * 仅在配合 {@link RegistryCenter} 使用时有意义。
     */
    private final String hostAddress;

    /** 服务端本地 DI 容器：接口名 → 实现对象（进程内反射调用） */
    private final ServiceRegistry serviceRegistry;

    /**
     * 网络注册中心（可选）：将服务地址发布到 ZK / Nacos 等外部注册中心。
     * 与 {@link ServiceRegistry}（本地 DI 容器）是两个不同层次的概念。
     */
    private final RegistryCenter registryCenter;

    /**
     * 等待向注册中心发布的服务元数据（host/port 在 start() 后才能填入完整）。
     * key = interfaceName，value = 部分填充的 ServiceMeta
     */
    private final List<ServiceMeta> pendingMetas = new ArrayList<>();

    /** start() 成功后实际注册到网络注册中心的完整列表，用于 stop() 时注销 */
    private volatile List<ServiceMeta> registeredMetas = null;

    private final List<Filter> filters = new ArrayList<>();

    /** 业务线程池：将反射调用从 Netty IO 线程卸载 */
    private final ExecutorService businessExecutor;

    // Netty 生命周期引用
    private volatile Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    // ---- 构造器 ----

    /**
     * 单机模式：不接入网络注册中心
     */
    public RpcServer(int port) {
        this(port, null, null);
    }

    /**
     * 集群模式：配合网络注册中心
     *
     * @param port           监听端口
     * @param hostAddress    本机对外暴露的地址（IP 或主机名），注册中心存储此地址供消费者连接
     * @param registryCenter 注册中心实现（{@link top.truism.blog.rpc.registry.ZookeeperRegistryCenter} 等）
     */
    public RpcServer(int port, String hostAddress, RegistryCenter registryCenter) {
        this.port            = port;
        this.hostAddress     = hostAddress;
        this.registryCenter  = registryCenter;
        this.serviceRegistry = new ServiceRegistry();
        this.businessExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r, "rpc-biz-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                });
    }

    // ---- 服务注册（链式） ----

    /**
     * 添加服务端拦截器（链式调用）。必须在 {@link #start()} 前调用。
     */
    public RpcServer addFilter(Filter filter) {
        filters.add(filter);
        return this;
    }

    /**
     * 注册服务实现（无版本）
     */
    public RpcServer register(Class<?> interfaceClass, Object impl) {
        return register(interfaceClass, "", impl);
    }

    /**
     * 注册服务实现（带版本）
     */
    public RpcServer register(Class<?> interfaceClass, String version, Object impl) {
        serviceRegistry.register(interfaceClass.getName(), version, impl);
        // 记录待发布元数据（host/port 在 start() 后补全）
        pendingMetas.add(ServiceMeta.builder()
                .interfaceName(interfaceClass.getName())
                .version(version)
                .build());
        return this;
    }

    // ---- 生命周期 ----

    /**
     * 启动服务端：绑定端口 → 发布到注册中心 → 阻塞直到 {@link #stop()} 被调用。
     */
    public void start() throws InterruptedException {
        bossGroup  = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
                            p.addLast(new LengthFieldBasedFrameDecoder(
                                    MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, 0, 0));
                            p.addLast(new RpcMessageCodec());
                            p.addLast(new RpcServerHandler(serviceRegistry, businessExecutor,
                                    new FilterChain(filters)));
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("RpcServer started on port {}", port);

            // 端口绑定成功后再发布，确保消费者连接时服务已就绪
            publishToRegistry();

            serverChannel.closeFuture().sync();
        } finally {
            doShutdown();
        }
    }

    /**
     * 优雅关闭：注销服务 → 关闭监听 channel → 释放线程池和注册中心。
     */
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
    }

    // ---- 私有：注册中心集成 ----

    /**
     * 将所有已通过 {@link #register} 登记的服务发布到网络注册中心。
     * 若未配置 registryCenter 或 hostAddress 则跳过。
     */
    private void publishToRegistry() {
        if (registryCenter == null || hostAddress == null) {
            return;
        }
        String effectiveHost = hostAddress.isBlank() ? "127.0.0.1" : hostAddress;
        List<ServiceMeta> published = new ArrayList<>();

        for (ServiceMeta pending : pendingMetas) {
            ServiceMeta full = ServiceMeta.builder()
                    .interfaceName(pending.getInterfaceName())
                    .version(pending.getVersion())
                    .group(pending.getGroup() != null ? pending.getGroup() : "")
                    .host(effectiveHost)
                    .port(port)
                    .build();
            registryCenter.register(full);
            published.add(full);
        }

        this.registeredMetas = published;
        log.info("Published {} service(s) to registry as {}:{}", published.size(), effectiveHost, port);
    }

    /**
     * 从注册中心注销所有服务（stop() 时调用）。
     */
    private void unpublishFromRegistry() {
        if (registryCenter == null || registeredMetas == null) return;
        registeredMetas.forEach(meta -> {
            try {
                registryCenter.unregister(meta);
            } catch (Exception e) {
                log.warn("Failed to unregister {} from registry", meta.getServiceKey(), e);
            }
        });
        try {
            registryCenter.close();
        } catch (Exception e) {
            log.warn("Failed to close registry center", e);
        }
    }

    private void doShutdown() {
        unpublishFromRegistry();
        businessExecutor.shutdown();
        if (bossGroup  != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("RpcServer shutdown");
    }
}
