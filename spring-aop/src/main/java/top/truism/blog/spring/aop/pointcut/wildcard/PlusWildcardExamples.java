package top.truism.blog.spring.aop.pointcut.wildcard;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class PlusWildcardExamples {

    // ========== 1. 匹配接口及其所有实现类 ==========

    // 匹配 BaseService 及其所有子类型的方法
    @Pointcut("within(top.truism.blog.spring.aop.pointcut.wildcard.BaseService+)")
    public void baseServiceAndSubtypes() {}
    // 匹配：
    //   UserServiceImpl.process()
    //   UserServiceImpl.createUser()
    //   AdminUserService.process()
    //   AdminUserService.createUser()

    // 匹配 UserService 及其所有实现类的方法
    @Pointcut("within(top.truism.blog.spring.aop.pointcut.wildcard.UserService+)")
    public void userServiceAndImplementations() {}
    // 匹配：
    //   UserServiceImpl.process()
    //   UserServiceImpl.createUser()
    //   AdminUserService.process()
    //   AdminUserService.createUser()

    // ========== 2. 匹配类及其所有子类 ==========

    // 假设有以下类层次：
    // public abstract class BaseController { }
    // public class UserController extends BaseController { }
    // public class OrderController extends BaseController { }

    @Pointcut("within(top.truism.blog.spring.aop.pointcut.wildcard.BaseController+)")
    public void baseControllerAndSubclasses() {}
    // 匹配：
    //   UserController 的所有方法
    //   OrderController 的所有方法
    // 不匹配：
    //   BaseController 的方法（如果是抽象类）

    // ========== 3. 结合其他表达式使用 ==========

    // 匹配 BaseService 及其子类型的所有 public 方法
    @Pointcut("within(top.truism.blog.spring.aop.pointcut.wildcard.BaseService+) && execution(public * *(..))")
    public void publicBaseServiceMethods() {}

    // 匹配 BaseService 及其子类型的 create 开头的方法
    @Pointcut("within(top.truism.blog.spring.aop.pointcut.wildcard.BaseService+) && execution(* create*(..))")
    public void createMethodsInBaseService() {}

}
