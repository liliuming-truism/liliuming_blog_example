package top.truism.blog.redis.codec;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class RespEncoder {

    private static final byte[] CRLF = {'\r', '\n'};

    public void encode(ByteBuf out, RespValue value) {
        switch (value.type()) {
            case SIMPLE_STRING -> encodeSimpleString(out, value.asString());
            case BULK_STRING -> encodeBulkString(out, value);
            case INTEGER -> encodeInteger(out, value.asLong());
            case ARRAY -> encodeArray(out, value.asArray());
            case ERROR -> encodeError(out, value.asString());
            case NULL -> encodeNull(out);
        }
    }

    private void encodeSimpleString(ByteBuf out, String data) {
        out.writeByte('+');
        out.writeBytes(data.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeBulkString(ByteBuf out, RespValue value) {
        byte[] data = getBytes(value.data());
        if (data == null) {
            out.writeBytes("$-1\r\n".getBytes(StandardCharsets.UTF_8));
        } else {
            out.writeByte('$');
            out.writeBytes(String.valueOf(data.length).getBytes(StandardCharsets.UTF_8));
            out.writeBytes(CRLF);
            out.writeBytes(data);
            out.writeBytes(CRLF);
        }
    }

    private void encodeInteger(ByteBuf out, long value) {
        out.writeByte(':');
        out.writeBytes(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeArray(ByteBuf out, List<RespValue> elements) {
        if (elements == null) {
            out.writeBytes("*-1\r\n".getBytes(StandardCharsets.UTF_8));
        } else {
            out.writeByte('*');
            out.writeBytes(String.valueOf(elements.size()).getBytes(StandardCharsets.UTF_8));
            out.writeBytes(CRLF);
            for (RespValue element : elements) {
                encode(out, element);
            }
        }
    }

    private void encodeError(ByteBuf out, String message) {
        out.writeByte('-');
        out.writeBytes(message.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeNull(ByteBuf out) {
        out.writeBytes("$-1\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] getBytes(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof byte[] bytes) {
            return bytes;
        }
        if (data instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Unsupported data type: " + data.getClass());
    }
}