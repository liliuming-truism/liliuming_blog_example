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
import top.truism.blog.rpc.registry.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RPC 服务端
 *
 * <p>使用示例：
 * <pre>{@code
 * RpcServer server = new RpcServer(8888);
 * server.register(HelloService.class, new HelloServiceImpl());
 * server.start();   // 阻塞直到 stop() 被调用
 * }</pre>
 *
 * <p>优雅关闭：
 * <pre>{@code
 * server.stop();    // 关闭监听、释放所有线程池
 * }</pre>
 */
@Slf4j
public class RpcServer {

    /** 最大帧长度 16 MB */
    private static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    /**
     * LengthFieldBasedFrameDecoder 参数说明：
     * <pre>
     * 协议头前15字节：magic(2)+version(1)+type(1)+ser(1)+comp(1)+status(1)+requestId(8)
     * lengthFieldOffset  = 15  → 长度字段从第15字节开始
     * lengthFieldLength  = 4   → 长度字段占4字节
     * lengthAdjustment   = 0   → 长度值即为 body 长度，无需调整
     * initialBytesToStrip= 0   → 保留完整帧，由 RpcMessageCodec 解析头部
     * </pre>
     */
    private static final int LENGTH_FIELD_OFFSET = 15;
    private static final int LENGTH_FIELD_LENGTH = 4;

    private final int port;
    private final ServiceRegistry serviceRegistry;
    private final List<Filter> filters = new ArrayList<>();
    /**
     * 业务线程池：承接从 Netty IO 线程卸载的反射调用，避免阻塞 Worker 线程。
     * 线程数默认 = CPU 核心数 * 2，适合以计算/反射为主的业务场景。
     */
    private final ExecutorService businessExecutor;

    // 持有引用以支持 stop()
    private volatile Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public RpcServer(int port) {
        this.port = port;
        this.serviceRegistry = new ServiceRegistry();
        this.businessExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r, "rpc-biz-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * 添加服务端拦截器（链式调用友好）
     *
     * <p>过滤器按添加顺序执行。必须在 {@link #start()} 前调用。
     */
    public RpcServer addFilter(Filter filter) {
        filters.add(filter);
        return this;
    }

    /**
     * 注册服务实现（无版本，链式调用友好）
     */
    public RpcServer register(Class<?> interfaceClass, Object impl) {
        serviceRegistry.register(interfaceClass.getName(), impl);
        return this;
    }

    /**
     * 注册服务实现（带版本，链式调用友好）
     */
    public RpcServer register(Class<?> interfaceClass, String version, Object impl) {
        serviceRegistry.register(interfaceClass.getName(), version, impl);
        return this;
    }

    /**
     * 启动服务端，阻塞直到 {@link #stop()} 被调用或进程退出。
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
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
                            // 60s 无读事件则触发 IdleStateEvent，RpcServerHandler 负责关闭空闲连接
                            p.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
                            // 粘包/拆包处理：按帧切割
                            p.addLast(new LengthFieldBasedFrameDecoder(
                                    MAX_FRAME_LENGTH,
                                    LENGTH_FIELD_OFFSET,
                                    LENGTH_FIELD_LENGTH,
                                    0, 0));
                            // 编解码
                            p.addLast(new RpcMessageCodec());
                            // 业务处理（卸载到 businessExecutor，带 Filter 链）
                            p.addLast(new RpcServerHandler(serviceRegistry, businessExecutor,
                                    new FilterChain(filters)));
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("RpcServer started on port {}", port);
            serverChannel.closeFuture().sync();
        } finally {
            doShutdown();
        }
    }

    /**
     * 优雅关闭：关闭监听 channel，等待 Netty EventLoop 和业务线程池退出。
     */
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
    }

    private void doShutdown() {
        businessExecutor.shutdown();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("RpcServer shutdown");
    }
}
