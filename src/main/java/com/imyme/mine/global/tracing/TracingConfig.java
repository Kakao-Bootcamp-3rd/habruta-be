package com.imyme.mine.global.tracing;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
public class TracingConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SpanHandler hostIpSpanHandler(HostInfoProvider hostInfoProvider) {
        return new HostIpSpanHandler(hostInfoProvider.getHostIp());
    }

    private static class HostIpSpanHandler extends SpanHandler {

        private final String hostIp;

        HostIpSpanHandler(String hostIp) {
            this.hostIp = hostIp;
        }

        @Override
        public boolean begin(TraceContext context, MutableSpan span, TraceContext parent) {
            if (parent == null) {
                span.tag("host.ip", hostIp);
            }
            return true;
        }

        @Override
        public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            return !isMonitoringRequest(span);
        }

        private boolean isMonitoringRequest(MutableSpan span) {
            String route = span.tag("http.route");
            return isExcludedPath(route) || isExcludedPath(span.name());
        }

        private boolean isExcludedPath(String path) {
            return path != null && (
                path.equals("/health")
                    || path.equals("/server/health")
                    || path.equals("/actuator/health")
                    || path.equals("/actuator/prometheus")
                    || path.startsWith("GET /health")
                    || path.startsWith("GET /server/health")
                    || path.startsWith("GET /actuator/health")
                    || path.startsWith("GET /actuator/prometheus")
            );
        }
    }
}
