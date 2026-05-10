package top.truism.blog.redis.bootstrap;

import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.redis.codec.RespValue;

@Slf4j
public class IdleHandler extends IdleStateHandler {

    public IdleHandler() {
        super(0, 30, 0);
    }
}