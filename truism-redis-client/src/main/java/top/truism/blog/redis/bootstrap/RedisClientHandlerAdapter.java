package top.truism.blog.redis.bootstrap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.redis.codec.RespValue;

@Slf4j
public abstract class RedisClientHandlerAdapter extends SimpleChannelInboundHandler<RespValue> {

    protected final PendingRequests pendingRequests;

    protected RedisClientHandlerAdapter(PendingRequests pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RespValue msg) throws Exception {
        log.trace("Received: type={}", msg.type());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // handled in RedisClientHandler
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel inactive");
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception: {}", cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }
}