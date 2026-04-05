package top.truism.blog.starter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties.class)
public class TruismLoggingAutoConfiguration {

    private static final Logger log = LogManager.getLogger(TruismLoggingAutoConfiguration.class);

    public TruismLoggingAutoConfiguration(LoggingProperties props,
        ObjectProvider<Environment> envProvider) {
        // 尝试填充服务信息字段（一次性）
        var env = envProvider.getIfAvailable();
        String service = props.getService();
        if (service == null || service.isBlank()) {
            if (env != null) {
                service = env.getProperty("spring.application.name", "unknown-service");
                props.setService(service);
            } else {
                service = "unknown-service";
            }
        }
        // 放入 ThreadContext 静态标签（每线程复制时可复用）
        ThreadContext.put("service", service);
        ThreadContext.put("env", props.getEnv());
        ThreadContext.put("region", props.getRegion());
        log.info("AcmeLogging initialized: service={}, env={}, region={}", service, props.getEnv(), props.getRegion());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(name = "acmeTraceIdFilter")
    public Filter acmeTraceIdFilter(LoggingProperties props) {
        if (!props.isEnableTraceFilter()) {
            log.info("TraceIdFilter disabled by property acme.logging.enable-trace-filter=false");
            return new Filter() {

                @Override
                public void doFilter(ServletRequest request, ServletResponse response,
                    FilterChain chain) throws IOException, ServletException {

                }
            };
        }
        log.info("Registering TraceIdFilter (header={})", props.getTraceHeader());
        return new TraceIdFilter(props);
    }
}
