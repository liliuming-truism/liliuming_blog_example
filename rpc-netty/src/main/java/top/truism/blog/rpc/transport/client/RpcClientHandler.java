package top.truism.blog.rpc.transport.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.protocol.MessageType;
import top.truism.blog.rpc.protocol.RpcMessage;
import top.truism.blog.rpc.protocol.SerializationType;

/**
 * 客户端响应处理器
 * <p>
 * 收到响应时，通过 {@link PendingRequests} 找到对应的 CompletableFuture 并完成它。
 * 读空闲时发送心跳 PING，维持长连接。
 */
@Slf4j
@RequiredArgsConstructor
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private final PendingRequests pendingRequests;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        if (msg.getMessageType() == MessageType.HEARTBEAT_PONG) {
            log.debug("Heartbeat pong received from {}", ctx.channel().remoteAddress());
            return;
        }

        // 普通响应：唤醒等待的 CompletableFuture
        pendingRequests.complete(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 读空闲：发送心跳 PING
            RpcMessage ping = RpcMessage.builder()
                    .messageType(MessageType.HEARTBEAT_PING)
                    .serializationType(SerializationType.JDK)
                    .compress((byte) 0)
                    .status((byte) 0)
                    .requestId(0L)
                    .build();
            ctx.writeAndFlush(ping);
            log.debug("Heartbeat ping -> {}", ctx.channel().remoteAddress());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 连接断开，让所有挂起请求快速失败
        pendingRequests.failAll(new RuntimeException("Connection closed: " + ctx.channel().remoteAddress()));
        log.warn("Channel inactive: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client handler exception", cause);
        ctx.close();
    }
}
