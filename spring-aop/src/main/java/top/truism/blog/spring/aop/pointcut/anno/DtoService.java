package top.truism.blog.spring.aop.pointcut.anno;

import org.springframework.stereotype.Service;

@Service
public class DtoService {
    public String handle(Object obj) { return "ok"; }
    public String handle2(Object a, Object b) { return "ok2"; }
}
