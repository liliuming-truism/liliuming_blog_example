package top.truism.blog.spring.aop.proxy.cglib;

import java.lang.reflect.Method;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

/**
 * 方法拦截器
 */
public class UserServiceInterceptor implements MethodInterceptor {

    /**
     * 拦截方法调用
     * @param obj 代理对象本身
     * @param method 被拦截的方法对象
     * @param args 方法参数
     * @param proxy 方法代理对象，用于调用父类方法
     * @return 方法返回值
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] args,
        MethodProxy proxy) throws Throwable {

        System.out.println("=== CGLIB代理开始 ===");
        System.out.println("目标方法: " + method.getName());

        long startTime = System.currentTimeMillis();

        // 调用父类方法（目标方法）
        // 方式1：通过MethodProxy调用（推荐，性能更好）
        Object result = proxy.invokeSuper(obj, args);

        // 方式2：通过反射调用
        // Object result = method.invoke(obj, args); // 注意：这会导致死循环！

        long endTime = System.currentTimeMillis();

        System.out.println("执行耗时: " + (endTime - startTime) + "ms");
        System.out.println("=== CGLIB代理结束 ===\n");

        return result;
    }
}

