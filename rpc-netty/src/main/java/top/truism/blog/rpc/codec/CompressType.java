package top.truism.blog.rpc.codec;

import lombok.Getter;

/**
 * 压缩类型
 *
 * <p>对应协议帧头的 compress 字节。0 = 不压缩；1 = gzip。
 */
@Getter
public enum CompressType {

    NONE((byte) 0, "不压缩"),
    GZIP((byte) 1, "gzip");

    private final byte code;
    private final String desc;

    CompressType(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CompressType of(byte code) {
        for (CompressType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown compress type code: " + code);
    }
}
