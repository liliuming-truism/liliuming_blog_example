package top.truism.blog.redis.bootstrap;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.redis.codec.RespCodec;
import top.truism.blog.redis.codec.RespValue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisClient {

    private final String host;
    private final int port;
    private final NioEventLoopGroup group = new NioEventLoopGroup();
    private final PendingRequests pendingRequests = new PendingRequests();
    private final long requestIdGen = 0;
    private volatile Channel channel;

    public RedisClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(io.netty.channel.socket.nio.NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new IdleStateHandler(0, 30, 0),
                                new RespCodec(),
                                new RedisClientHandler(pendingRequests)
                        );
                    }
                });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        channel = future.channel();
        log.info("Connected to Redis at {}:{}", host, port);
    }

    public CompletableFuture<RespValue> sendCommand(RespValue command) {
        CompletableFuture<RespValue> future = new CompletableFuture<>();
        if (channel == null || !channel.isActive()) {
            future.completeExceptionally(new RuntimeException("Not connected"));
            return future;
        }

        long requestId = requestIdGen;
        pendingRequests.put(requestId, future);

        channel.writeAndFlush(command).addListener(f -> {
            if (!f.isSuccess()) {
                pendingRequests.remove(requestId);
                future.completeExceptionally(f.cause());
            }
        });

        // timeout
        channel.eventLoop().schedule(() -> {
            if (!future.isDone()) {
                pendingRequests.remove(requestId);
                future.completeExceptionally(new RuntimeException("Request timeout"));
            }
        }, 5, TimeUnit.SECONDS);

        return future;
    }

    public void shutdown() {
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
        log.info("Redis client shutdown");
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }
}