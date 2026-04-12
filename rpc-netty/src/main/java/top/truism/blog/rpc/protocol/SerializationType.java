package top.truism.blog.rpc.protocol;

import lombok.Getter;

/**
 * 序列化类型
 */
@Getter
public enum SerializationType {

    JDK((byte) 0, "JDK原生序列化"),
    JSON((byte) 1, "JSON序列化"),
    PROTOBUF((byte) 2, "Protobuf序列化");

    private final byte code;
    private final String desc;

    SerializationType(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static SerializationType of(byte code) {
        for (SerializationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown serialization type code: " + code);
    }
}
