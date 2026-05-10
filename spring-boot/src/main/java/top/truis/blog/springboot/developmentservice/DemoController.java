package top.truis.blog.springboot.developmentservice;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    public DemoController(
        JdbcTemplate jdbcTemplate,
        StringRedisTemplate redisTemplate,
        RocketMQTemplate rocketMQTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @GetMapping("/demo/mysql")
    public String mysql() {
        Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
        return "MySQL OK: " + result;
    }

    @GetMapping("/demo/redis")
    public String redis() {
        redisTemplate.opsForValue().set("demo:key", "hello redis");
        String value = redisTemplate.opsForValue().get("demo:key");
        return "Redis OK: " + value;
    }

    @GetMapping("/demo/rocketmq")
    public String rocketmq() {
        rocketMQTemplate.convertAndSend("demo-topic", "hello rocketmq");
        return "RocketMQ message sent";
    }
}
