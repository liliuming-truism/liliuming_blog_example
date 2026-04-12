package top.truism.blog.rpc.transport.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.codec.RpcMessageCodec;
import top.truism.blog.rpc.protocol.RpcMessage;
import top.truism.blog.rpc.protocol.RpcRequest;
import top.truism.blog.rpc.protocol.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC 客户端
 *
 * <p>使用示例：
 * <pre>{@code
 * RpcClient client = new RpcClient("127.0.0.1", 8888);
 * RpcResponse resp = client.sendRequest(request).get(3, TimeUnit.SECONDS);
 * }</pre>
 *
 * <p>线程安全，可复用同一实例并发发送多个请求。
 */
@Slf4j
public class RpcClient {

    private static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;
    private static final int LENGTH_FIELD_OFFSET = 15;
    private static final int LENGTH_FIELD_LENGTH = 4;

    private final String host;
    private final int port;

    private final NioEventLoopGroup eventLoopGroup;
    private final PendingRequests pendingRequests;
    private final AtomicLong requestIdGen;

    /** 复用单一连接，生产环境可替换为连接池 */
    private volatile Channel channel;

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.pendingRequests = new PendingRequests();
        this.requestIdGen = new AtomicLong(0);
    }

    /**
     * 连接到服务端，必须在发送请求前调用
     */
    public void connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // 30s 无读事件触发心跳
                        p.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                        p.addLast(new LengthFieldBasedFrameDecoder(
                                MAX_FRAME_LENGTH,
                                LENGTH_FIELD_OFFSET,
                                LENGTH_FIELD_LENGTH,
                                0, 0));
                        p.addLast(new RpcMessageCodec());
                        p.addLast(new RpcClientHandler(pendingRequests));
                    }
                });

        channel = bootstrap.connect(host, port).sync().channel();
        log.info("RpcClient connected to {}:{}", host, port);
    }

    /**
     * 发送 RPC 请求（使用默认 JDK 序列化、不压缩），返回异步 Future
     *
     * @param request 请求体
     * @return CompletableFuture，持有服务端的 RpcResponse
     */
    public CompletableFuture<RpcResponse> sendRequest(RpcRequest request) {
        RpcMessage message = RpcMessage.builder()
                .messageType(top.truism.blog.rpc.protocol.MessageType.REQUEST)
                .serializationType(top.truism.blog.rpc.protocol.SerializationType.JDK)
                .compress((byte) 0)
                .status((byte) 0)
                .requestId(requestIdGen.incrementAndGet())
                .body(request)
                .build();
        return sendMessage(message).thenApply(msg -> (RpcResponse) msg.getBody());
    }

    /**
     * 发送预先构建好的 RpcMessage，可自定义序列化类型和压缩方式
     *
     * @param message 完整的 RPC 消息（调用方负责填写 requestId、serializationType、compress 等）
     * @return CompletableFuture，持有原始 RpcMessage 响应
     */
    public CompletableFuture<RpcMessage> sendMessage(RpcMessage message) {
        long requestId = message.getRequestId() != 0
                ? message.getRequestId()
                : requestIdGen.incrementAndGet();

        // 确保 requestId 字段已填入
        if (message.getRequestId() == 0) {
            message = RpcMessage.builder()
                    .messageType(message.getMessageType())
                    .serializationType(message.getSerializationType())
                    .compress(message.getCompress())
                    .status(message.getStatus())
                    .requestId(requestId)
                    .body(message.getBody())
                    .build();
        }

        CompletableFuture<RpcMessage> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        final long fRequestId = requestId;
        channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                pendingRequests.complete(RpcMessage.builder()
                        .requestId(fRequestId)
                        .body(RpcResponse.fail("Send failed: " + f.cause().getMessage()))
                        .build());
                log.error("Send message {} failed", fRequestId, f.cause());
            }
        });
        return future;
    }

    /** 节点地址，格式 {@code host:port} */
    public String getAddress() {
        return host + ":" + port;
    }

    /** 当前连接是否活跃 */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 关闭客户端，释放资源
     */
    public void shutdown() {
        if (channel != null) {
            channel.close();
        }
        eventLoopGroup.shutdownGracefully();
        log.info("RpcClient shutdown");
    }
}
