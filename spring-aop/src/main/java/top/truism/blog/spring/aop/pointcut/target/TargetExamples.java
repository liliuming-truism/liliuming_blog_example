package top.truism.blog.spring.aop.pointcut.target;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TargetExamples {

    // ========== 1. 匹配接口类型 ==========
    @Pointcut("target(top.truism.blog.spring.aop.pointcut.target.UserService)")
    public void targetUserService() {}
    // 说明：匹配目标对象实现了 UserService 接口的方法

    // ========== 2. 匹配实现类类型 ==========
    @Pointcut("target(top.truism.blog.spring.aop.pointcut.target.UserServiceImpl)")
    public void targetUserServiceImpl() {}
    // 说明：匹配目标对象是 UserServiceImpl 类型的方法
    // 注意：无论哪种代理方式都能匹配

    @Before("targetUserService()")
    public void beforeInvoke() {
        log.info("Target pointcut for interface");
    }

    @Before("targetUserServiceImpl()")
    public void beforeInvokeForImpl() {
        log.info("Target pointcut for impl");
    }
}
