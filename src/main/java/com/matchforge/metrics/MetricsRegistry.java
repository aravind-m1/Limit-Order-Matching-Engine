package com.matchforge.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Lightweight metrics registry: counters plus a fixed-bucket latency
 * histogram (nanoseconds) recorded on the matching hot path.
 */
public final class MetricsRegistry {

    private final AtomicLong ordersProcessed = new AtomicLong();
    private final AtomicLong tradesExecuted = new AtomicLong();

    // Bucket upper-bounds in nanoseconds: <1us, <10us, <100us, <1ms, <10ms, >=10ms
    private final long[] bucketBoundsNanos = {1_000, 10_000, 100_000, 1_000_000, 10_000_000, Long.MAX_VALUE};
    private final AtomicLongArray buckets = new AtomicLongArray(bucketBoundsNanos.length);

    public void incrementOrders() { ordersProcessed.incrementAndGet(); }
    public void incrementTrades() { tradesExecuted.incrementAndGet(); }

    public void recordMatchLatency(long nanos) {
        for (int i = 0; i < bucketBoundsNanos.length; i++) {
            if (nanos < bucketBoundsNanos[i]) {
                buckets.incrementAndGet(i);
                return;
            }
        }
    }

    public long getOrdersProcessed() { return ordersProcessed.get(); }
    public long getTradesExecuted() { return tradesExecuted.get(); }

    public String renderText() {
        StringBuilder sb = new StringBuilder();
        sb.append("orders_processed ").append(ordersProcessed.get()).append('\n');
        sb.append("trades_executed ").append(tradesExecuted.get()).append('\n');
        String[] labels = {"lt_1us", "lt_10us", "lt_100us", "lt_1ms", "lt_10ms", "gte_10ms"};
        for (int i = 0; i < labels.length; i++) {
            sb.append("match_latency_bucket{le=\"").append(labels[i]).append("\"} ")
              .append(buckets.get(i)).append('\n');
        }
        return sb.toString();
    }
}
