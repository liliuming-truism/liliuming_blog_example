package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class TargetApplication implements ApplicationContextAware {

    public static void main(String[] args) {
        SpringApplication.run(TargetApplication.class, args);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 在这里调用需要使用applicationContext的方法
        if (applicationContext != null) {
            RuntimeMarkedService markedService = (RuntimeMarkedService) applicationContext.getBean("runtimeMarkedService");
            markedService.ping();

            RuntimeSubService subService = applicationContext.getBean(RuntimeSubService.class);
            subService.ping();
            subService.subOnly();
        }

    }

}
