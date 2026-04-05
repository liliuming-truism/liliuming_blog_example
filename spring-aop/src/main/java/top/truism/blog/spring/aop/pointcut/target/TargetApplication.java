package top.truism.blog.spring.aop.pointcut.target;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import top.truism.blog.spring.aop.pointcut.this2.ThisService;

@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = false, exposeProxy = true)
@Slf4j
public class TargetApplication implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        TargetApplication.applicationContext = applicationContext;
    }

    public static void main(String[] args) {
        SpringApplication.run(TargetApplication.class, args);

        Map<String, UserService>  userServices = applicationContext.getBeansOfType(UserService.class);

        UserService userServiceImpl = userServices.get("userServiceImpl");
        userServiceImpl.thisProcess();

        UserService childUserServiceImpl = userServices.get("childUserServiceImpl");
        childUserServiceImpl.thisProcess();

    }

}
