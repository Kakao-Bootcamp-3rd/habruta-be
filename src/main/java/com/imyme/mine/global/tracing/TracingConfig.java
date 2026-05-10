package com.imyme.mine.global.tracing;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Bean
    public SpanHandler hostIpSpanHandler(HostInfoProvider hostInfoProvider) {
        return new HostIpSpanHandler(hostInfoProvider.getPrivateIp());
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
    }
}
