package top.truism.blog.spring.aop.pointcut.execution;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@SpringBootApplication
public class ExecutionApplication implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ExecutionApplication.applicationContext = applicationContext;
    }

    public static void main(String[] args) {
        SpringApplication.run(ExecutionApplication.class, args);

        applicationContext.getBean(UserService.class).createUser("execution");

        applicationContext.getBean(UserService.class).createUser("execution", "email");
    }

}
