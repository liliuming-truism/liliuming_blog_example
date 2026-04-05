package top.truism.blog.spring.aop.pointcut.this2;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ThisAspectExample {
    @Before("this(top.truism.blog.spring.aop.pointcut.this2.ThisService)")
    public void beforeUserServiceProxy(JoinPoint joinPoint) {
        log.info("代理对象是 ThisService 类型: {}", joinPoint.getSignature().getName());
    }

    // 示例2：匹配代理对象是 UserServiceImpl 类型的方法
    @Before("this(top.truism.blog.spring.aop.pointcut.this2.ThisServiceImpl)")
    public void beforeUserServiceImplProxy(JoinPoint joinPoint) {
        log.info("代理对象是 ThisServiceImpl 类型: {}", joinPoint.getSignature().getName());
    }

}
