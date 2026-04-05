package top.truism.blog.spring.aop.proxy.cglib;

import org.springframework.cglib.proxy.Enhancer;

/**
 * CGLIB代理示例
 */
public class CglibProxyExample {

    public static void main(String[] args) {
        // 创建Enhancer对象
        Enhancer enhancer = new Enhancer();

        // 设置父类（目标类）
        enhancer.setSuperclass(UserService.class);

        // 设置接口
        enhancer.setInterfaces(new Class[]{ProxyInterface.class});

        // 设置回调函数
        enhancer.setCallback(new UserServiceInterceptor());

        // 创建代理对象
        UserService proxy = (UserService) enhancer.create();

        ProxyInterface proxyInterface = (ProxyInterface) proxy;
        System.out.println(proxyInterface.getClass().getName());

        // 调用代理方法
        proxy.addUser("张三");
        proxy.deleteUser("李四");
    }
}

