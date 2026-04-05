package top.truism.blog.spring.aop.pointcut.within;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChildUserDetailService extends UserDetailService{

    @Override
    public void queryUser() {
        log.info("child queryUser");
    }

    @Override
    public void createUser(String username) {
        log.info("child createUser");
    }

    @Override
    protected void deleteUser(String username) {
        log.info("child deleteUser");
    }
}
