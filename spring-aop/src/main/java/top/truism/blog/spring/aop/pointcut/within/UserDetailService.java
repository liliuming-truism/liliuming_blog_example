package top.truism.blog.spring.aop.pointcut.within;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserDetailService {

    public void queryUser() {
        log.info("queryUser");
    }

    public void createUser(String username) {
        log.info("createUser");
    }

    protected void deleteUser(String username) {
        log.info("deleteUser");
    }

    private void cantProxy() {
        log.info("cantProxy");
    }


}
