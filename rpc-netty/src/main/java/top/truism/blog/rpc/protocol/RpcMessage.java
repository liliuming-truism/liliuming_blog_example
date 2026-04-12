package top.truism.blog.rpc.protocol;

import lombok.Builder;
import lombok.Data;

/**
 * RPC 消息封装
 * <p>
 * 协议帧格式（19字节固定头 + body）：
 * <pre>
 * +--------+--------+--------+--------+--------+--------+----------+-----------+
 * | magic  |version |  type  |  ser   |  comp  | status | requestId| bodyLength|
 * | 2bytes | 1byte  | 1byte  | 1byte  | 1byte  | 1byte  |  8bytes  |  4bytes   |
 * +--------+--------+--------+--------+--------+--------+----------+-----------+
 * |                              body (n bytes)                                 |
 * +-----------------------------------------------------------------------------+
 * </pre>
 */
@Data
@Builder
public class RpcMessage {

    /** 协议魔数，用于快速识别数据包 */
    public static final short MAGIC = (short) 0xCAFE;

    /** 协议版本 */
    public static final byte VERSION = 1;

    /** 固定头长度（字节数） */
    public static final int HEADER_LENGTH = 19;

    /** 消息类型 */
    private MessageType messageType;

    /** 序列化类型 */
    private SerializationType serializationType;

    /** 是否压缩（0=不压缩，1=gzip） */
    private byte compress;

    /**
     * 响应状态码（仅 RESPONSE 消息有效）
     * 0=成功，非0=失败
     */
    private byte status;

    /** 请求唯一ID，用于关联请求与响应 */
    private long requestId;

    /** 消息体（RpcRequest 或 RpcResponse） */
    private Object body;
}
