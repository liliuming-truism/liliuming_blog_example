package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class WithinApplication implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    public static void main(String[] args) {
        SpringApplication.run(WithinApplication.class, args);

        MarkedService markedService = (MarkedService) applicationContext.getBean("markedService");
        markedService.foo();
        markedService.inherited();

        SubService service = applicationContext.getBean(SubService.class);
        service.foo();
        service.inherited();

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        WithinApplication.applicationContext = applicationContext;
    }
}
