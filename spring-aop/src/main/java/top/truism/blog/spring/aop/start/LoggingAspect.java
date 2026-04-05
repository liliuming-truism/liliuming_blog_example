package top.truism.blog.spring.aop.start;

import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect  // 声明这是一个切面.
@Component  // 注册为 Spring Bean
@Order(1)  // 指定优先级
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    // 定义切点：service 层的所有方法
    @Pointcut("execution(* top.truism.blog.spring.aop.*.*(..))")
    public void serviceLayer() {
    }

    // 定义切点：带有 @Log 注解的方法
    @Pointcut("@annotation(top.truism.blog.spring.aop.start.Log)")
    public void logAnnotation() {
    }

    // 组合切点
    @Pointcut("serviceLayer() || logAnnotation()")
    public void loggableOperations() {
    }

    // 前置通知：记录方法调用
    @Before("loggableOperations()")
    public void logBefore(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        logger.info("调用方法: {}.{}，参数: {}",
            className, methodName, Arrays.toString(args));
    }

    // 返回后通知：记录返回值
    @AfterReturning(pointcut = "loggableOperations()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        logger.info("方法 {} 返回: {}", methodName, result);
    }

    // 异常通知：记录异常
    @AfterThrowing(pointcut = "loggableOperations()", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Exception ex) {
        String methodName = joinPoint.getSignature().getName();
        logger.error("方法 {} 异常: {}", methodName, ex.getMessage(), ex);
    }

    // 环绕通知：性能监控
    @Around("loggableOperations()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            logger.info("方法 {} 执行耗时: {}ms", methodName, (endTime - startTime));
            return result;
        } catch (Exception ex) {
            logger.error("方法 {} 执行失败", methodName, ex);
            throw ex;
        }
    }
}
