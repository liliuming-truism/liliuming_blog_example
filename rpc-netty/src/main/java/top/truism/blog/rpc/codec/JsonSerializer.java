package top.truism.blog.rpc.codec;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Jackson JSON 序列化实现
 *
 * <p>开启 {@code DefaultTyping.NON_FINAL}，在 JSON 中嵌入 {@code @class} 类型元数据，
 * 保证 {@code Object[]} 参数列表的反序列化准确性。
 *
 * <p>{@code Class<?>} 类型通过自定义序列化器转换为类全限定名字符串。
 */
@Slf4j
public class JsonSerializer implements Serializer {

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 对非 final 类型启用多态类型标记（@class 字段），解决 Object[] 反序列化问题
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Class<?> 自定义序列化：class 对象 ↔ 类全限定名字符串
        SimpleModule module = new SimpleModule("ClassModule");
        module.addSerializer(Class.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(Class value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.getName());
            }
        });
        module.addDeserializer(Class.class, new JsonDeserializer<>() {
            @Override
            public Class<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String name = p.getText();
                try {
                    return switch (name) {
                        case "int"     -> int.class;
                        case "long"    -> long.class;
                        case "double"  -> double.class;
                        case "float"   -> float.class;
                        case "boolean" -> boolean.class;
                        case "byte"    -> byte.class;
                        case "short"   -> short.class;
                        case "char"    -> char.class;
                        default        -> Class.forName(name);
                    };
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Cannot resolve class: " + name, e);
                }
            }
        });
        mapper.registerModule(module);
        return mapper;
    }

    @Override
    public byte[] serialize(Object obj) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(obj);
            log.trace("JSON serialize: type={} size={}bytes", obj.getClass().getSimpleName(), bytes.length);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException("JSON serialize failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            T obj = MAPPER.readValue(bytes, clazz);
            log.trace("JSON deserialize: type={} size={}bytes", clazz.getSimpleName(), bytes.length);
            return obj;
        } catch (IOException e) {
            throw new RuntimeException("JSON deserialize failed", e);
        }
    }
}
