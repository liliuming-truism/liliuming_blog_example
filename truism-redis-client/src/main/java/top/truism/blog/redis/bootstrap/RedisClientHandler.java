package top.truism.blog.redis.bootstrap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.redis.codec.RespValue;

import java.util.List;

@Slf4j
public class RedisClientHandler extends RedisClientHandlerAdapter {

    private long requestIdCounter = 0;

    public RedisClientHandler(PendingRequests pendingRequests) {
        super(pendingRequests);
    }

    public long newRequestId() {
        return requestIdCounter++;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle) {
            if (idle.state() == IdleState.READER_IDLE) {
                log.debug("Reader idle, sending PING");
                ctx.writeAndFlush(RespValue.array(List.of(RespValue.bulkString("PING"))));
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Connection closed: {}", ctx.channel().remoteAddress());
        pendingRequests.failAll(new RuntimeException("Connection closed"));
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception: {}", cause.getMessage());
        ctx.close();
    }
}