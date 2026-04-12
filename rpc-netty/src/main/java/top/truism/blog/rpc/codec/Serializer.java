package top.truism.blog.rpc.codec;

/**
 * 序列化接口，支持多种序列化实现
 */
public interface Serializer {

    /**
     * 序列化对象为字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化字节数组为对象
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
