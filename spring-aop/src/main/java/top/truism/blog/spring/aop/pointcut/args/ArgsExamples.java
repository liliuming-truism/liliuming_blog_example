package top.truism.blog.spring.aop.pointcut.args;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ArgsExamples {

    // ========== 1. 匹配特定参数类型 ==========
    @Pointcut("args(String)")
    public void singleStringArg() {}
    // 说明：匹配只有一个 String 参数的方法

    @Pointcut("args(String, int)")
    public void stringAndIntArgs() {}
    // 说明：匹配有 String 和 int 两个参数的方法

    @Pointcut("args(Long, ..)")
    public void longFirstArg() {}
    // 说明：匹配第一个参数是 Long，其他参数任意的方法

    @Pointcut("args(.., String)")
    public void stringLastArg() {}
    // 说明：匹配最后一个参数是 String，其他参数任意的方法

    // ========== 2. 绑定参数值 ==========
    @Pointcut("args(userId, ..)")
    public void withUserId(Long userId) {}
    // 说明：匹配第一个参数是 Long 的方法，并绑定参数值

    @Before(value = "singleStringArg()")
    public void before() {

    }
}

