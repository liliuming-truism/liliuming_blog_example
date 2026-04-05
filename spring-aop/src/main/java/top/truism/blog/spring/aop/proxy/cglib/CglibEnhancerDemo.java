package top.truism.blog.spring.aop.proxy.cglib;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.FixedValue;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;

public class CglibEnhancerDemo {

    // ========== 额外接口（用于 setInterfaces）==========
    public interface Auditable {
        String audit();
    }

    // ========== 被增强的父类（用于 setSuperclass）==========
    public static class OrderService {
        private String name;

        public OrderService() {
            this("default");
        }

        public OrderService(String name) {
            this.name = name;

            // 构造期间调用可覆盖方法：观察 interceptDuringConstruction 的影响
            // 若 interceptDuringConstruction=true，这里可能会触发回调拦截
            init();
        }

        protected void init() {
            System.out.println("[OrderService.init] name=" + name);
        }

        public String placeOrder(String item) {
            System.out.println("[OrderService.placeOrder] item=" + item);
            return "ORDER-" + item.toUpperCase();
        }

        public String query(String orderId) {
            System.out.println("[OrderService.query] orderId=" + orderId);
            return "OK:" + orderId;
        }

        public final String finalMethod() {
            return "FINAL"; // final 方法无法被 cglib 拦截
        }

        @Override
        public String toString() {
            return "OrderService{name='" + name + "'}";
        }
    }

    // ========== 回调1：通用方法拦截（MethodInterceptor）==========
    public static class TimingInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            long start = System.nanoTime();
            try {
                System.out.println("[TimingInterceptor] before " + method.getName() + ", args=" + Arrays.toString(args));
                Object ret = proxy.invokeSuper(obj, args);
                System.out.println("[TimingInterceptor] after  " + method.getName() + ", ret=" + ret);
                return ret;
            } finally {
                long cost = System.nanoTime() - start;
                System.out.println("[TimingInterceptor] cost(ns)=" + cost);
            }
        }
    }

    // ========== 回调2：固定返回值（FixedValue）==========
    public static class FixedAuditValue implements FixedValue {
        @Override
        public Object loadObject() {
            return "AUDIT-OK"; // audit() 永远返回这个
        }
    }

    // ========== 回调3：不拦截（NoOp）==========
    // 直接用 NoOp.INSTANCE

    // ========== 过滤器：为不同方法选择不同 Callback ==========
    public static class MyCallbackFilter implements CallbackFilter {
        @Override
        public int accept(Method method) {
            String name = method.getName();

            // 0 -> TimingInterceptor：业务方法做计时
            if ("placeOrder".equals(name) || "query".equals(name)) return 0;

            // 1 -> FixedValue：audit() 走固定返回值
            if ("audit".equals(name)) return 1;

            // 2 -> NoOp：toString / init / 其他方法不拦截
            return 2;
        }
    }

    public static void main(String[] args) throws Exception {
        demo_create_with_callbacks_and_filter();
        System.out.println("\n--------------------------------------------\n");
        demo_single_setCallback_create_noarg();
        System.out.println("\n--------------------------------------------\n");
        demo_createClass_with_callbackTypes();
    }

    /**
     * 覆盖点：
     * - setSuperclass
     * - setInterfaces
     * - setCallbacks
     * - setCallbackFilter
     * - setInterceptDuringConstruction
     * - create(argTypes, args)
     */
    private static void demo_create_with_callbacks_and_filter() {
        Enhancer enhancer = new Enhancer();

        // 1) 设置父类与接口
        enhancer.setSuperclass(OrderService.class);
        enhancer.setInterfaces(new Class[]{Auditable.class});

        // 2) 设置多个回调 + 过滤器
        Callback[] callbacks = new Callback[]{
            new TimingInterceptor(),   // index 0
            new FixedAuditValue(),     // index 1
            NoOp.INSTANCE              // index 2
        };
        enhancer.setCallbacks(callbacks);
        enhancer.setCallbackFilter(new MyCallbackFilter());

        // 3) 构造期间是否拦截：建议你改 true/false 观察输出差异
        enhancer.setInterceptDuringConstruction(false);

        // 4) 指定构造器参数创建
        OrderService proxy = (OrderService) enhancer.create(
            new Class[]{String.class},
            new Object[]{"Alice"}
        );

        // 调用父类方法：placeOrder/query 会走 TimingInterceptor（index 0）
        String orderId = proxy.placeOrder("book");
        proxy.query(orderId);

        // 调用接口方法：audit 会走 FixedValue（index 1）
        Auditable auditable = (Auditable) proxy;
        System.out.println("[main] audit() => " + auditable.audit());

        // NoOp：toString 不拦截
        System.out.println("[main] toString => " + proxy);

        // final 方法：无法拦截
        System.out.println("[main] finalMethod => " + proxy.finalMethod());
    }

    /**
     * 覆盖点：
     * - setSuperclass
     * - setCallback (单回调)
     * - create() (无参构造)
     */
    private static void demo_single_setCallback_create_noarg() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(OrderService.class);

        // 单回调：所有可拦截方法默认都走它
        enhancer.setCallback(new TimingInterceptor());

        // 无参构造创建
        OrderService proxy = (OrderService) enhancer.create();
        proxy.placeOrder("pen");
        System.out.println("[main] toString => " + proxy);
    }

    /**
     * 覆盖点：
     * - setSuperclass
     * - setCallbackTypes / setCallbackType（只指定类型）
     * - createClass()（只生成 Class，不创建实例）
     *
     * 说明：
     * 这里用 callbackTypes 演示“预生成类”。
     * 生成 Class 后，我们再用 Enhancer#create(...) 正常创建实例（更直观）。
     * （在框架里也常见：createClass 后通过 Factory 机制绑定 callbacks 做复用缓存）
     */
    private static void demo_createClass_with_callbackTypes() throws Exception {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(OrderService.class);

        // 只指定回调类型（不提供实例）
        enhancer.setCallbackTypes(new Class[]{
            MethodInterceptor.class, // index 0
            FixedValue.class,        // index 1
            NoOp.class               // index 2
        });
        enhancer.setCallbackFilter(new MyCallbackFilter());

        Class<?> proxyClass = enhancer.createClass();
        System.out.println("[main] proxyClass = " + proxyClass.getName());

        // 为了让示例更容易跑通，这里再用“有实例的 enhancer”去创建对象（同一套过滤逻辑）
        Enhancer enhancer2 = new Enhancer();
        enhancer2.setSuperclass(OrderService.class);
        enhancer2.setInterfaces(new Class[]{Auditable.class});
        enhancer2.setCallbacks(new Callback[]{new TimingInterceptor(), new FixedAuditValue(), NoOp.INSTANCE});
        enhancer2.setCallbackFilter(new MyCallbackFilter());

        OrderService proxy = (OrderService) enhancer2.create(new Class[]{String.class}, new Object[]{"Bob"});
        System.out.println("[main] placeOrder => " + proxy.placeOrder("coffee"));
        System.out.println("[main] audit => " + ((Auditable) proxy).audit());
    }
}

