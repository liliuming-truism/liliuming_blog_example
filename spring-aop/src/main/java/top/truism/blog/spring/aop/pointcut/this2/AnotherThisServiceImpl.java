package top.truism.blog.spring.aop.pointcut.this2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnotherThisServiceImpl implements ThisService{

    @Override
    public void thisProcess() {
        log.info("another this service thisProcess");
    }
}
