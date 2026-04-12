package top.truism.blog.rpc.transport.server;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.filter.FilterChain;
import top.truism.blog.rpc.protocol.*;
import top.truism.blog.rpc.registry.ServiceRegistry;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

/**
 * 服务端请求处理器
 *
 * <p>职责：接收 RpcRequest，将业务反射调用卸载到 {@code businessExecutor}（避免阻塞
 * Netty IO 线程），将结果封装为 RpcResponse 返回。
 *
 * <p>处理空闲连接：读空闲超时后主动关闭 channel，释放服务端资源。
 */
@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private final ServiceRegistry serviceRegistry;
    /** 业务线程池：反射调用在此执行，不阻塞 Netty IO 线程 */
    private final ExecutorService businessExecutor;
    /** 服务端拦截器链（AccessLog、TPS 限流等），在反射调用前后执行 */
    private final FilterChain filterChain;

    public RpcServerHandler(ServiceRegistry serviceRegistry, ExecutorService businessExecutor,
                            FilterChain filterChain) {
        this.serviceRegistry = serviceRegistry;
        this.businessExecutor = businessExecutor;
        this.filterChain = filterChain;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        MessageType type = msg.getMessageType();

        if (type == MessageType.HEARTBEAT_PING) {
            handleHeartbeat(ctx, msg);
            return;
        }

        if (type == MessageType.REQUEST) {
            // 卸载到业务线程，IO 线程立即返回
            businessExecutor.execute(() -> handleRequest(ctx, msg));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 读空闲：客户端心跳超时，主动关闭连接释放资源
            log.warn("Channel idle timeout, closing {}", ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, RpcMessage ping) {
        RpcMessage pong = RpcMessage.builder()
                .messageType(MessageType.HEARTBEAT_PONG)
                .serializationType(ping.getSerializationType())
                .compress((byte) 0)
                .status((byte) 0)
                .requestId(ping.getRequestId())
                .build();
        ctx.writeAndFlush(pong);
        log.debug("Heartbeat pong -> {}", ctx.channel().remoteAddress());
    }

    private void handleRequest(ChannelHandlerContext ctx, RpcMessage requestMsg) {
        RpcRequest request = (RpcRequest) requestMsg.getBody();
        RpcResponse response;

        try {
            // 通过 FilterChain 执行拦截链，终态节点为本地反射调用
            response = filterChain.execute(request, req -> {
                Object serviceImpl = serviceRegistry.lookup(req.getInterfaceName(), req.getVersion());
                Method method = serviceImpl.getClass().getMethod(
                        req.getMethodName(), req.getParameterTypes());
                Object result = method.invoke(serviceImpl, req.getParameters());
                log.debug("Invoke {}.{}() success", req.getInterfaceName(), req.getMethodName());
                return RpcResponse.success(result);
            });
        } catch (Exception e) {
            log.error("Invoke {}.{}() failed", request.getInterfaceName(), request.getMethodName(), e);
            response = RpcResponse.fail(e.getMessage());
        }

        RpcMessage responseMsg = RpcMessage.builder()
                .messageType(MessageType.RESPONSE)
                .serializationType(requestMsg.getSerializationType())
                .compress((byte) 0)
                .status(response.getStatus())
                .requestId(requestMsg.getRequestId())
                .body(response)
                .build();

        ctx.writeAndFlush(responseMsg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Server handler exception, closing channel {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
