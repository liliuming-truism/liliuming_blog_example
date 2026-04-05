package top.truism.blog.spring.aop.pointcut.execution;

public class User {

    private String username;

    private String email;

    public User() {
    }

    public User(String username) {
        this.username = username;
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
