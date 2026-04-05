package top.truism.blog.spring.aop.pointcut.anno;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

// 切面：匹配任何在声明类带 @RoleMarked 的方法
@Aspect
@Component
public class WithinAspect {

    @Before("@within(RoleMarked)")
    public void beforeWithin(JoinPoint jp) {
        System.out.println("[@within] " + jp.getSignature());
    }
}
