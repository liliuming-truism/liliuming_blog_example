package top.truism.blog.rpc;

/**
 * 服务端本地实现
 */
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }

    @Override
    public int add(int a, int b) {
        return a + b;
    }
}
