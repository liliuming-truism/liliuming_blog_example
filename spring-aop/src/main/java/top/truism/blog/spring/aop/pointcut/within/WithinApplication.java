package top.truism.blog.spring.aop.pointcut.within;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@SpringBootApplication
public class WithinApplication implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        WithinApplication.applicationContext = applicationContext;
    }

    public static void main(String[] args) {
        SpringApplication.run(WithinApplication.class, args);

        ((UserDetailService)applicationContext.getBean("userDetailService")).queryUser();
        ((UserDetailService)applicationContext.getBean("userDetailService")).deleteUser("createUser");
        ((UserDetailService)applicationContext.getBean("userDetailService")).createUser("createUser");

        applicationContext.getBean(ChildUserDetailService.class).queryUser();
        applicationContext.getBean(ChildUserDetailService.class).deleteUser("createUser");
        applicationContext.getBean(ChildUserDetailService.class).createUser("createUser");
    }
}
