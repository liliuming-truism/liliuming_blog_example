package top.truism.blog.redis.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RespDecoder extends ReplayingDecoder<RespDecoder.State> {

    public enum State {
        READ_TYPE,
        READ_BULK_LENGTH,
        READ_BULK_CONTENT,
        READ_SIMPLE_STRING,
        READ_INTEGER,
        READ_ERROR,
        READ_ARRAY_LENGTH,
        READ_ARRAY_ELEMENT
    }

    public RespDecoder() {
        super(State.READ_TYPE);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case READ_TYPE -> readType(in, out);
            default -> throw new IllegalStateException("Unexpected state: " + state());
        }
    }

    private void readType(ByteBuf in, List<Object> out) {
        byte type = in.readByte();
        switch (type) {
            case '+' -> {
                checkpoint(State.READ_SIMPLE_STRING);
                readSimpleString(in, out);
            }
            case '$' -> {
                checkpoint(State.READ_BULK_LENGTH);
                readBulkLength(in, out);
            }
            case ':' -> {
                checkpoint(State.READ_INTEGER);
                readInteger(in, out);
            }
            case '*' -> {
                checkpoint(State.READ_ARRAY_LENGTH);
                readArrayLength(in, out);
            }
            case '-' -> {
                checkpoint(State.READ_ERROR);
                readError(in, out);
            }
            default -> throw new IllegalStateException("Unknown RESP type: " + (char) type);
        }
    }

    private void readSimpleString(ByteBuf in, List<Object> out) {
        String value = readLine(in);
        out.add(RespValue.simpleString(value));
        checkpoint(State.READ_TYPE);
    }

    private void readError(ByteBuf in, List<Object> out) {
        String message = readLine(in);
        out.add(RespValue.error(message));
        checkpoint(State.READ_TYPE);
    }

    private void readInteger(ByteBuf in, List<Object> out) {
        String line = readLine(in);
        long value = Long.parseLong(line);
        out.add(RespValue.integer(value));
        checkpoint(State.READ_TYPE);
    }

    private void readBulkLength(ByteBuf in, List<Object> out) {
        String line = readLine(in);
        long length = Long.parseLong(line);
        if (length == -1) {
            out.add(RespValue.nullValue());
            checkpoint(State.READ_TYPE);
        } else if (length == 0) {
            out.add(RespValue.bulkString(""));
            checkpoint(State.READ_TYPE);
        } else {
            checkpoint(State.READ_BULK_CONTENT);
            readBulkContent(in, out, (int) length);
        }
    }

    private void readBulkContent(ByteBuf in, List<Object> out, int length) {
        ByteBuf bytes = in.readBytes(length);
        byte[] data = new byte[length];
        bytes.readBytes(data);
        in.readByte();
        in.readByte();
        out.add(RespValue.bulkString(data));
        checkpoint(State.READ_TYPE);
    }

    private void readArrayLength(ByteBuf in, List<Object> out) {
        String line = readLine(in);
        long length = Long.parseLong(line);
        if (length == -1) {
            out.add(RespValue.nullValue());
            checkpoint(State.READ_TYPE);
        } else if (length == 0) {
            out.add(RespValue.array(List.of()));
            checkpoint(State.READ_TYPE);
        } else {
            ArrayBuilder builder = new ArrayBuilder((int) length);
            checkpoint(State.READ_ARRAY_ELEMENT);
            readArrayElements(in, out, builder, 0);
        }
    }

    private void readArrayElements(ByteBuf in, List<Object> out, ArrayBuilder builder, int index) {
        if (index >= builder.expected) {
            out.add(RespValue.array(builder.build()));
            checkpoint(State.READ_TYPE);
            return;
        }
        byte type = in.readByte();
        switch (type) {
            case '+' -> {
                String value = readLine(in);
                builder.add(RespValue.simpleString(value));
            }
            case '$' -> {
                String line = readLine(in);
                long len = Long.parseLong(line);
                if (len == -1) {
                    builder.add(RespValue.nullValue());
                } else {
                    ByteBuf bytes = in.readBytes((int) len);
                    byte[] data = new byte[(int) len];
                    bytes.readBytes(data);
                    in.readByte();
                    in.readByte();
                    builder.add(RespValue.bulkString(data));
                }
            }
            case ':' -> {
                String line = readLine(in);
                builder.add(RespValue.integer(Long.parseLong(line)));
            }
            case '*' -> {
                String line = readLine(in);
                long len = Long.parseLong(line);
                if (len == -1) {
                    builder.add(RespValue.nullValue());
                } else if (len == 0) {
                    builder.add(RespValue.array(List.of()));
                } else {
                    builder.add(readNestedArray(in, (int) len));
                }
            }
            case '-' -> {
                String msg = readLine(in);
                builder.add(RespValue.error(msg));
            }
            default -> throw new IllegalStateException("Unknown type in array: " + (char) type);
        }
        readArrayElements(in, out, builder, index + 1);
    }

    private RespValue readNestedArray(ByteBuf in, int length) {
        List<RespValue> elements = new java.util.ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            byte type = in.readByte();
            switch (type) {
                case '+' -> elements.add(RespValue.simpleString(readLine(in)));
                case '$' -> {
                    String line = readLine(in);
                    long len = Long.parseLong(line);
                    if (len == -1) {
                        elements.add(RespValue.nullValue());
                    } else {
                        ByteBuf bytes = in.readBytes((int) len);
                        byte[] data = new byte[(int) len];
                        bytes.readBytes(data);
                        in.readByte();
                        in.readByte();
                        elements.add(RespValue.bulkString(data));
                    }
                }
                case ':' -> elements.add(RespValue.integer(Long.parseLong(readLine(in))));
                case '*' -> elements.add(readNestedArray(in, (int) Long.parseLong(readLine(in))));
                case '-' -> elements.add(RespValue.error(readLine(in)));
                default -> throw new IllegalStateException("Unknown nested array type: " + (char) type);
            }
        }
        return RespValue.array(elements);
    }

    private String readLine(ByteBuf in) {
        ByteBuf line = in.readBytes(readLineLength(in));
        byte b = in.readByte();
        if (b != '\r') {
            throw new IllegalStateException("Expected \\r, got " + (char) b);
        }
        byte n = in.readByte();
        if (n != '\n') {
            throw new IllegalStateException("Expected \\n, got " + (char) n);
        }
        byte[] data = new byte[line.readableBytes()];
        line.readBytes(data);
        return new String(data);
    }

    private int readLineLength(ByteBuf in) {
        int count = 0;
        while (true) {
            byte b = in.readByte();
            if (b == '\r') {
                in.readerIndex(in.readerIndex() - 1);
                break;
            }
            count++;
        }
        return count;
    }

    private static class ArrayBuilder {
        final int expected;
        final List<RespValue> elements;

        ArrayBuilder(int expected) {
            this.expected = expected;
            this.elements = new java.util.ArrayList<>(expected);
        }

        void add(RespValue value) {
            elements.add(value);
        }

        List<RespValue> build() {
            return elements;
        }
    }
}