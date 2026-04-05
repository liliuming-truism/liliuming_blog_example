package top.truism.blog.spring.aop.pointcut.anno;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ArgsAspect {

    // 单参数：要求该实参的运行时类带 @Sensitive
    @Before("@args(Sensitive)")
    public void beforeArgs1(JoinPoint jp) {
        System.out.println("[@args single] " + jp.getSignature());
    }

    // 多参数按位置匹配：第一个参数带 @Sensitive，第二个不限
    @Before("@args(Sensitive, ..)")
    public void beforeArgs2(JoinPoint jp) {
        System.out.println("[(@args first param sensitive)] " + jp.getSignature());
    }
}
