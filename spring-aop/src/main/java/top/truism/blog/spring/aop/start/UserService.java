package top.truism.blog.spring.aop.start;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserService {

    // 连接点1: createUser 方法执行
    @Log
    public void createUser(String username) {
        // 连接点2: 方法执行前
        System.out.println("创建用户: " + username);
        // 连接点3: 方法执行后
        // 连接点4: 方法正常返回后
        // 连接点5: 方法抛出异常后
    }

    // 连接点6: deleteUser 方法执行
    public void deleteUser(Long userId) {
        System.out.println("删除用户: " + userId);
    }

    // 连接点7: getUserInfo 方法执行
    public String getUserInfo(Long userId) {
        return "用户信息: " + userId;
    }
}

