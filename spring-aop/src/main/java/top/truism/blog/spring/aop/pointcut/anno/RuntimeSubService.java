package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.stereotype.Component;

@Component
public class RuntimeSubService extends RuntimeMarkedService {
    public String subOnly() { return "sub"; }
}
