package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class ArgsApplication implements ApplicationContextAware {

    public static void main(String[] args) {
        SpringApplication.run(ArgsApplication.class, args);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        DtoService dtoService = applicationContext.getBean(DtoService.class);

        dtoService.handle(new SensitiveDto("x"));

        dtoService.handle(new PlainDto("x"));

        dtoService.handle(null);

        dtoService.handle2(new SensitiveDto("a"), new PlainDto("b"));

        dtoService.handle2(new PlainDto("a"), new SensitiveDto("b"));

    }
}
