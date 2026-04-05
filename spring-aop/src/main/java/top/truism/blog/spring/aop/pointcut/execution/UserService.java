package top.truism.blog.spring.aop.pointcut.execution;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserService {

    public User createUser() {
        return new User();
    }

    public User createUser(String username) {
        return new User(username);
    }

    public User createUser(String username, String email) {
        return new User(username, email);
    }

    public void deleteUser(Long userId) {
        // 删除用户
    }

    public User getUserById(Long userId) {
        log.info("getUserById:{}", userId);
        return new User();
    }

    public List<User> listUsers() {
        log.info("listUsers");
        return new ArrayList<>();
    }

    private void internalMethod() {
        // 内部方法
    }
}