package com.imyme.mine.global.logging;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate StatementInspector — 요청 단위 SQL 실행 횟수 추적
 * [로컬 측정용] 커밋 금지
 */
public class QueryCountInterceptor implements StatementInspector {

    private static final ThreadLocal<Long> QUERY_COUNT = ThreadLocal.withInitial(() -> 0L);

    @Override
    public String inspect(String sql) {
        QUERY_COUNT.set(QUERY_COUNT.get() + 1);
        return sql;
    }

    public static long getCount() {
        return QUERY_COUNT.get();
    }

    public static void reset() {
        QUERY_COUNT.set(0L);
    }

    public static void clear() {
        QUERY_COUNT.remove();
    }
}
