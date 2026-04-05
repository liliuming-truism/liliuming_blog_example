package top.truism.blog.spring.aop.start;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@SpringBootApplication
public class LoggingAspectDemo implements ApplicationContextAware {



    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        LoggingAspectDemo.applicationContext = applicationContext;
    }

    public static void main(String[] args) {
        SpringApplication.run(LoggingAspectDemo.class, args);

        UserService userService = applicationContext.getBean(UserService.class);

        userService.createUser("zhangsan");

    }

}
