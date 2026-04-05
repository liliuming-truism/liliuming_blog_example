package top.truism.blog.spring.aop.pointcut.within;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class WithinAspect {

    @Pointcut("within(top.truism.blog.spring.aop.pointcut.within.UserDetailService+)")
    public void withinUserDetail() {}

    @Pointcut("within(top.truism.blog.spring.aop.pointcut.within.*UserDetailService)")
    public void withinWildcardUserDetail() {}

    @Before("withinUserDetail()")
    public void before() {
        log.info("proxy for within userDetail");
    }

    @AfterReturning(value = "withinWildcardUserDetail()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        log.info("afterReturning result={}", result);
    }

}
