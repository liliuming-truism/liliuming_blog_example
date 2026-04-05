package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.stereotype.Component;

@RoleMarked("runtime")
@Component
public class RuntimeMarkedService {
    public String ping() { return "pong"; }
}
