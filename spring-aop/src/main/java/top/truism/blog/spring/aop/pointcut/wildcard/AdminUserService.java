package top.truism.blog.spring.aop.pointcut.wildcard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdminUserService implements UserService{

    @Override
    public void createUser(String username) {

    }

    @Override
    public void process() {

    }
}
