package com.imyme.mine.global.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TraceSupport {

    private final Tracer tracer;

    public <T> T trace(String spanName, Supplier<T> supplier) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return supplier.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public void trace(String spanName, Runnable runnable) {
        trace(spanName, () -> {
            runnable.run();
            return null;
        });
    }
}
