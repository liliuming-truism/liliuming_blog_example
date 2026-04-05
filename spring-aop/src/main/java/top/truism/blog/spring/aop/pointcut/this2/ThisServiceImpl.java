package top.truism.blog.spring.aop.pointcut.this2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ThisServiceImpl implements ThisService{

    @Override
    public void thisProcess() {
        log.info("thisProcess");
    }
}
