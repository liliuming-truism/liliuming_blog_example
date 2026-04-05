package top.truism.blog.spring.aop.pointcut.anno;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TargetAspect {

    @Before("@target(RoleMarked)")
    public void beforeTarget(JoinPoint jp) {
        System.out.println("[@target] " + jp.getSignature());
    }
}
