package top.truism.blog.spring.aop.pointcut.execution;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class ExecutionExamples {

    // ========== 2. 匹配特定返回值类型 ==========
    @Pointcut("execution(String *(..))")  // 返回 String
    public void stringReturnMethods() {}

    @Pointcut("execution(public * top.truism.blog.spring.aop.pointcut.execution..create*(..))")  // 匹配所有 public 返回 void
    public void publicMethods() {}

    @Pointcut("execution(* *(..))")  // 任意返回值
    public void anyReturnMethods() {}

    @Pointcut("execution(top.truism.blog.spring.aop.pointcut.execution.User *(..))")
    public void anyReturnUserMethods() {}

    // ========== 3. 匹配特定类的方法 ==========
    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.*(..))")
    public void userServiceMethods() {}

    // ========== 4. 匹配特定方法名 ==========
    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.create*(..))")
    public void allCreateUser() {}

    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.get*(..))")  // get 开头
    public void getterMethods() {}

    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.set*(..))")  // set 开头
    public void setterMethods() {}

    // ========== 5. 匹配特定参数 ==========
    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.createUser(String))")  // 单个 String 参数
    public void createUserWithString() {}

    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.createUser(String, String))")  // String 和 int 参数
    public void createUserWithTwoString() {}

    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.createUser(..))")  // 任意参数
    public void createUserWithAnyParams() {}

    @Pointcut("execution(* top.truism.blog.spring.aop.pointcut.execution.UserService.createUser())")  // 无参数
    public void createUserWithNoParams() {}

    /*******************************下面是通知使用连接点示例*********************************/

    // 示例1：拦截所有 public 方法
    @Before("publicMethods()")
    public void beforePublicMethods(JoinPoint joinPoint) {
        log.info("执行 public 方法: {}", joinPoint.getSignature().getName());
    }

    // 示例2：拦截所有 create 开头的方法
    @Before("allCreateUser()")
    public void beforeCreateMethods(JoinPoint joinPoint) {
        log.info("执行 create 方法: {}", joinPoint.getSignature().getName());
    }

    // 示例3：拦截返回 User 对象的方法
    @AfterReturning(
        pointcut = "anyReturnUserMethods()",
        returning = "user"
    )
    public void afterReturningUser(User user) {
        log.info("返回 User 对象: {}", user);
    }


    // 示例4：拦截特定方法签名
    @Around("createUserWithTwoString()")
    public Object aroundCreateUser(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("创建用户前");
        Object result = joinPoint.proceed();
        log.info("创建用户后");
        return result;
    }

}
