package top.truism.blog.rpc.codec;

import top.truism.blog.rpc.protocol.SerializationType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 序列化器工厂
 */
public class SerializerFactory {

    private static final Map<SerializationType, Serializer> SERIALIZERS = new EnumMap<>(SerializationType.class);

    static {
        SERIALIZERS.put(SerializationType.JDK, new JdkSerializer());
        SERIALIZERS.put(SerializationType.JSON, new JsonSerializer());
    }

    public static Serializer get(SerializationType type) {
        Serializer serializer = SERIALIZERS.get(type);
        if (serializer == null) {
            throw new UnsupportedOperationException("Unsupported serialization type: " + type);
        }
        return serializer;
    }
}
