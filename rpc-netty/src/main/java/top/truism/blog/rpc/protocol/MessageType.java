package top.truism.blog.rpc.protocol;

import lombok.Getter;

/**
 * RPC 消息类型
 */
@Getter
public enum MessageType {

    REQUEST((byte) 0, "请求"),
    RESPONSE((byte) 1, "响应"),
    HEARTBEAT_PING((byte) 2, "心跳请求"),
    HEARTBEAT_PONG((byte) 3, "心跳响应");

    private final byte code;
    private final String desc;

    MessageType(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MessageType of(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
