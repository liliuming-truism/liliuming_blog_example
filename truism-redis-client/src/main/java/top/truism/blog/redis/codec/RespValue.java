package top.truism.blog.redis.codec;

import java.util.List;

public record RespValue(RespType type, Object data) {

    public enum RespType {
        SIMPLE_STRING,
        BULK_STRING,
        INTEGER,
        ARRAY,
        ERROR,
        NULL
    }

    public static RespValue simpleString(String value) {
        return new RespValue(RespType.SIMPLE_STRING, value);
    }

    public static RespValue bulkString(String value) {
        return new RespValue(RespType.BULK_STRING, value);
    }

    public static RespValue bulkString(byte[] value) {
        return new RespValue(RespType.BULK_STRING, value);
    }

    public static RespValue integer(long value) {
        return new RespValue(RespType.INTEGER, value);
    }

    public static RespValue array(List<RespValue> elements) {
        return new RespValue(RespType.ARRAY, elements);
    }

    public static RespValue error(String message) {
        return new RespValue(RespType.ERROR, message);
    }

    public static RespValue nullValue() {
        return new RespValue(RespType.NULL, null);
    }

    public boolean isNull() {
        return type == RespType.NULL;
    }

    public long asLong() {
        if (type == RespType.INTEGER && data instanceof Long l) {
            return l;
        }
        if (data instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException("Cannot convert " + type + " to long");
    }

    public String asString() {
        if (data == null) {
            return null;
        }
        if (data instanceof String s) {
            return s;
        }
        if (data instanceof byte[] bytes) {
            return new String(bytes);
        }
        throw new IllegalStateException("Cannot convert " + type + " to String");
    }

    @SuppressWarnings("unchecked")
    public List<RespValue> asArray() {
        if (type == RespType.ARRAY && data instanceof List<?> list) {
            return (List<RespValue>) list;
        }
        throw new IllegalStateException("Cannot convert " + type + " to Array");
    }
}