package top.truism.blog.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;
import top.truism.blog.rpc.protocol.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * RPC 消息编解码器（合并编码器和解码器）
 *
 * <p>配套使用 {@link io.netty.handler.codec.LengthFieldBasedFrameDecoder} 处理粘包/拆包，
 * 参数：maxFrameLength=16MB, lengthFieldOffset=15, lengthFieldLength=4, lengthAdjustment=0, initialBytesToStrip=0
 *
 * <p>编码（出站）：将 RpcMessage 写入 ByteBuf
 * <p>解码（入站）：从完整帧的 ByteBuf 还原 RpcMessage
 */
@Slf4j
public class RpcMessageCodec extends ByteToMessageCodec<RpcMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        // 1. magic (2 bytes)
        out.writeShort(RpcMessage.MAGIC);
        // 2. version (1 byte)
        out.writeByte(RpcMessage.VERSION);
        // 3. message type (1 byte)
        out.writeByte(msg.getMessageType().getCode());
        // 4. serialization type (1 byte)
        out.writeByte(msg.getSerializationType().getCode());
        // 5. compress (1 byte)
        out.writeByte(msg.getCompress());
        // 6. status (1 byte)
        out.writeByte(msg.getStatus());
        // 7. requestId (8 bytes)
        out.writeLong(msg.getRequestId());

        // 序列化 body
        byte[] bodyBytes = new byte[0];
        if (msg.getBody() != null) {
            Serializer serializer = SerializerFactory.get(msg.getSerializationType());
            bodyBytes = serializer.serialize(msg.getBody());
            // gzip 压缩
            if (msg.getCompress() == CompressType.GZIP.getCode()) {
                bodyBytes = gzip(bodyBytes);
            }
        }

        // 8. body length (4 bytes)
        out.writeInt(bodyBytes.length);
        // 9. body
        out.writeBytes(bodyBytes);

        log.trace("Encoded: requestId={} type={} ser={} compress={} bodyLength={}",
                msg.getRequestId(), msg.getMessageType(), msg.getSerializationType(),
                msg.getCompress(), bodyBytes.length);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 验证魔数
        short magic = in.readShort();
        if (magic != RpcMessage.MAGIC) {
            log.error("Invalid magic number: 0x{}, expected 0x{}, dropping connection {}",
                    Integer.toHexString(magic & 0xFFFF),
                    Integer.toHexString(RpcMessage.MAGIC & 0xFFFF),
                    ctx.channel().remoteAddress());
            throw new IllegalStateException("Invalid magic number: " + magic);
        }

        // 版本（暂不校验，保留扩展）
        in.readByte();

        MessageType messageType = MessageType.of(in.readByte());
        SerializationType serializationType = SerializationType.of(in.readByte());
        byte compress = in.readByte();
        byte status = in.readByte();
        long requestId = in.readLong();
        int bodyLength = in.readInt();

        RpcMessage.RpcMessageBuilder builder = RpcMessage.builder()
                .messageType(messageType)
                .serializationType(serializationType)
                .compress(compress)
                .status(status)
                .requestId(requestId);

        // 心跳消息无 body
        if (messageType == MessageType.HEARTBEAT_PING || messageType == MessageType.HEARTBEAT_PONG) {
            out.add(builder.build());
            return;
        }

        if (bodyLength > 0) {
            byte[] bodyBytes = new byte[bodyLength];
            in.readBytes(bodyBytes);

            // gzip 解压
            if (compress == CompressType.GZIP.getCode()) {
                bodyBytes = ungzip(bodyBytes);
            }

            Serializer serializer = SerializerFactory.get(serializationType);
            Class<?> bodyClass = messageType == MessageType.REQUEST ? RpcRequest.class : RpcResponse.class;
            Object body = serializer.deserialize(bodyBytes, bodyClass);
            builder.body(body);
        }

        RpcMessage decoded = builder.build();
        log.trace("Decoded: requestId={} type={} ser={} compress={} bodyLength={}",
                requestId, messageType, serializationType, compress, bodyLength);
        out.add(decoded);
    }

    // ---- 压缩工具 ----

    private static byte[] gzip(byte[] data) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(data);
            gos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("GZIP compress failed", e);
        }
    }

    private static byte[] ungzip(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("GZIP decompress failed", e);
        }
    }
}
