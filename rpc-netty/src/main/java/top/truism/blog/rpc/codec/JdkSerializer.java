package top.truism.blog.rpc.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * JDK 原生序列化实现
 */
@Slf4j
public class JdkSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            byte[] bytes = bos.toByteArray();
            log.trace("JDK serialize: type={} size={}bytes", obj.getClass().getSimpleName(), bytes.length);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException("JDK serialize failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            T obj = (T) ois.readObject();
            log.trace("JDK deserialize: type={} size={}bytes", clazz.getSimpleName(), bytes.length);
            return obj;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("JDK deserialize failed", e);
        }
    }
}
