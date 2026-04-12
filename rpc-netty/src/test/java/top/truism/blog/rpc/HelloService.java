package top.truism.blog.rpc;

/**
 * 示例服务接口（同时存在于 client 和 server 的公共 API）
 */
public interface HelloService {

    String sayHello(String name);

    int add(int a, int b);
}
