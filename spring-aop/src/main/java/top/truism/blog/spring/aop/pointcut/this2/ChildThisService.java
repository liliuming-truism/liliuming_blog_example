package top.truism.blog.spring.aop.pointcut.this2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChildThisService extends ThisServiceImpl{

    @Override
    public void thisProcess() {
        log.info("child this service thisProcess");
    }
}
