package top.truism.blog.spring.aop.pointcut.this2;

import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@Slf4j
public class ThisApplication implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ThisApplication.applicationContext = applicationContext;
    }

    public static void main(String[] args) throws Exception{
        SpringApplication.run(ThisApplication.class, args);

        Map<String, ThisService> proxyBeans = applicationContext.getBeansOfType(ThisService.class);
        ThisService proxy = proxyBeans.get("thisServiceImpl");
        proxy.thisProcess();
        printBeanInfo(proxy.getClass(), proxy);

        ThisService anotherProxy = proxyBeans.get("anotherThisServiceImpl");
        anotherProxy.thisProcess();
        printBeanInfo(anotherProxy.getClass(), anotherProxy);

        ThisService childThisService = proxyBeans.get("childThisService");
        childThisService.thisProcess();
        printBeanInfo(childThisService.getClass(), childThisService);

    }

    private static void printBeanInfo(Class<? extends ThisService> proxyClass, Object proxy) {
        System.out.println("=== 代理信息 ===");
        System.out.println("代理类名: " + proxyClass.getName());
        System.out.println("是否 JDK 代理: " + java.lang.reflect.Proxy.isProxyClass(proxyClass));
        System.out.println("是否 CGLIB 代理: " + proxyClass.getName().contains("$$"));
        System.out.println("instanceof ThisService: " + (proxy instanceof ThisService));
        System.out.println("instanceof ThisServiceImpl: " + (proxy instanceof ThisServiceImpl));
        System.out.println("instanceof ChildThisService: " + (proxy instanceof ChildThisService));
        System.out.println("父类: " + proxyClass.getSuperclass().getName());
        System.out.println("接口: " + Arrays.toString(proxyClass.getInterfaces()));
    }
}
