package com.eventledger.gateway.observability;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SERVICE_NAME_KEY = "service";

    private TraceContext() {
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(SERVICE_NAME_KEY, "event-gateway");
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SERVICE_NAME_KEY);
    }
}
