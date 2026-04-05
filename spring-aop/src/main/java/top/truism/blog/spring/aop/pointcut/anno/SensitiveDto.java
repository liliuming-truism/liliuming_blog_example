package top.truism.blog.spring.aop.pointcut.anno;

@Sensitive
public class SensitiveDto {
    String data;
    public SensitiveDto(String data) { this.data = data; }
}

