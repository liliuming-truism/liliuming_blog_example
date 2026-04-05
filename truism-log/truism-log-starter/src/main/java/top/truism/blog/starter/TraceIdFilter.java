package top.truism.blog.starter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.apache.logging.log4j.ThreadContext;

public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_KEY = "traceId";

    private final LoggingProperties props;

    public TraceIdFilter(LoggingProperties props) {
        this.props = props;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest http)) {
            chain.doFilter(request, response);
            return;
        }
        String headerName = props.getTraceHeader();
        String traceId = http.getHeader(headerName);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }
        ThreadContext.put(TRACE_ID_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            ThreadContext.remove(TRACE_ID_KEY);
        }
    }

    private String generateTraceId() {
        // 简易：去掉 '-'，可替换为雪花/ULID
        return UUID.randomUUID().toString().replace("-", "");
    }
}
