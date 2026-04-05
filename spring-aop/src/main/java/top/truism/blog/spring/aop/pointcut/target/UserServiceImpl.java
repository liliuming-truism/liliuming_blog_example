package top.truism.blog.spring.aop.pointcut.target;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserServiceImpl implements UserService {

    @Override
    public void thisProcess() {
        log.info("{} this process", this.getClass().getName());
    }
}
