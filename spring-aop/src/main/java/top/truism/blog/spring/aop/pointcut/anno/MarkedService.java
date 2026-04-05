package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.stereotype.Component;

@RoleMarked("service")
@Component
public class MarkedService {
    public String foo() { return "foo"; }
    public String inherited() { return "base"; }
}

