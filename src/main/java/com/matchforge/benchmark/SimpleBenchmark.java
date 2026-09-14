package com.matchforge.benchmark;

import com.matchforge.command.NewOrderCommand;
import com.matchforge.engine.MatchingCore;
import com.matchforge.marketdata.MarketDataPublisher;
import com.matchforge.matching.PriceTimePriorityStrategy;
import com.matchforge.metrics.MetricsRegistry;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.persistence.WalWriter;
import com.matchforge.sequencer.Sequencer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Hand-rolled micro-benchmark. NOTE: a production version of this project
 * would use JMH (proper warm-up handling, fork isolation, blackhole
 * consumption) via Maven/Gradle - this manual version exists because this
 * build environment has no access to Maven Central to pull the JMH
 * dependency. It still applies the core JMH discipline: a separate
 * warm-up phase, then a measured phase, with end-to-end latency sampled
 * per submitted order.
 */
public final class SimpleBenchmark {

    public static void main(String[] args) throws Exception {
        Path walPath = Files.createTempFile("limit-order-matching-engine-bench-wal", ".log");
        walPath.toFile().deleteOnExit();

        MarketDataPublisher publisher = new MarketDataPublisher();
        publisher.start();
        MetricsRegistry metrics = new MetricsRegistry();
        MatchingCore core = new MatchingCore("BTC-USD", new PriceTimePriorityStrategy(), publisher, metrics);
        core.start();

        WalWriter wal = new WalWriter(walPath, false); // async durability mode, for throughput measurement
        Sequencer sequencer = new Sequencer(wal, 0);
        sequencer.registerCore(core);
        sequencer.start();

        Random rnd = new Random(42);
        int warmupOrders = 50_000;
        int measuredOrders = 200_000;

        System.out.println("Warming up JIT with " + warmupOrders + " orders...");
        for (int i = 0; i < warmupOrders; i++) submitOne(sequencer, rnd);
        waitForDrain(metrics, warmupOrders);

        System.out.println("Measuring throughput and latency over " + measuredOrders + " orders...");
        long[] latencies = new long[measuredOrders];
        long startAll = System.nanoTime();
        for (int i = 0; i < measuredOrders; i++) {
            long submitStart = System.nanoTime();
            submitOne(sequencer, rnd);
            latencies[i] = System.nanoTime() - submitStart; // gateway-side enqueue latency
        }
        long endAll = System.nanoTime();
        waitForDrain(metrics, warmupOrders + measuredOrders);

        double seconds = (endAll - startAll) / 1_000_000_000.0;
        double throughput = measuredOrders / seconds;

        Arrays.sort(latencies);
        long p50 = latencies[(int) (measuredOrders * 0.50)];
        long p99 = latencies[(int) (measuredOrders * 0.99)];
        long p999 = latencies[(int) (measuredOrders * 0.999)];

        System.out.println();
        System.out.println("=== Benchmark Results ===");
        System.out.printf("Enqueue throughput: %.0f orders/sec%n", throughput);
        System.out.printf("Enqueue latency p50:   %,d ns%n", p50);
        System.out.printf("Enqueue latency p99:   %,d ns%n", p99);
        System.out.printf("Enqueue latency p99.9: %,d ns%n", p999);
        System.out.println("Total orders processed by matching core: " + metrics.getOrdersProcessed());
        System.out.println();
        System.out.println(metrics.renderText());

        sequencer.shutdown();
        core.shutdown();
        publisher.shutdown();
        wal.close();
    }

    private static void submitOne(Sequencer sequencer, Random rnd) {
        Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
        long price = 95 + rnd.nextInt(10);
        long qty = 1 + rnd.nextInt(20);
        // Spread across many distinct clients so self-trade prevention doesn't
        // suppress matches (a single shared client id would block every trade).
        String clientId = "bench-client-" + rnd.nextInt(50);
        Order o = Order.builder().clientId(clientId).symbol("BTC-USD").side(side).price(price).quantity(qty).build();
        sequencer.enqueue(new NewOrderCommand("BTC-USD", o));
    }

    private static void waitForDrain(MetricsRegistry metrics, long target) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (metrics.getOrdersProcessed() < target && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }
}
