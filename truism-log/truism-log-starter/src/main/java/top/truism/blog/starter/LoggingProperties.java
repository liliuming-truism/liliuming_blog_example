package top.truism.blog.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "truism.logging")
@Component
public class LoggingProperties {

    /**
     * 服务名称（默认从 spring.application.name 推导）
     */
    private String service;
    /**
     * 环境：dev / test / prod
     */
    private String env = "dev";
    /**
     * 机房或区域
     */
    private String region = "default";
    /**
     * 是否启用 traceId 过滤器
     */
    private boolean enableTraceFilter = true;
    /**
     * 请求 Header 中读取 traceId 的键
     */
    private String traceHeader = "X-Trace-Id";

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public boolean isEnableTraceFilter() { return enableTraceFilter; }
    public void setEnableTraceFilter(boolean enableTraceFilter) { this.enableTraceFilter = enableTraceFilter; }
    public String getTraceHeader() { return traceHeader; }
    public void setTraceHeader(String traceHeader) { this.traceHeader = traceHeader; }
}
