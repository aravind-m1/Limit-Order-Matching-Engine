package com.matchforge.tests;

import com.matchforge.command.NewOrderCommand;
import com.matchforge.engine.MatchingCore;
import com.matchforge.marketdata.MarketDataPublisher;
import com.matchforge.matching.PriceTimePriorityStrategy;
import com.matchforge.metrics.MetricsRegistry;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.persistence.WalWriter;
import com.matchforge.sequencer.Sequencer;
import com.matchforge.testharness.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.matchforge.testharness.Assertions.*;

public class SequencerConcurrencyStressTest {

    @Test
    public void manyProducerThreadsProduceGaplessTotalOrder() throws Exception {
        Path walPath = Files.createTempFile("wal-stress", ".log");
        walPath.toFile().deleteOnExit();

        WalWriter wal = new WalWriter(walPath, false);
        Sequencer sequencer = new Sequencer(wal, 0);

        MarketDataPublisher publisher = new MarketDataPublisher();
        publisher.start();
        MetricsRegistry metrics = new MetricsRegistry();
        MatchingCore core = new MatchingCore("BTC-USD", new PriceTimePriorityStrategy(), publisher, metrics);
        core.start();
        sequencer.registerCore(core);
        sequencer.start();

        int threadCount = 8;
        int ordersPerThread = 500;
        int totalOrders = threadCount * ordersPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                for (int i = 0; i < ordersPerThread; i++) {
                    Order o = Order.builder()
                            .clientId("client-" + threadIdx)
                            .symbol("BTC-USD")
                            .side(i % 2 == 0 ? Side.BUY : Side.SELL)
                            .price(100 + (i % 5))
                            .quantity(1)
                            .build();
                    sequencer.enqueue(new NewOrderCommand("BTC-USD", o));
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        long deadline = System.currentTimeMillis() + 5000;
        while (metrics.getOrdersProcessed() < totalOrders && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertEquals(totalOrders, metrics.getOrdersProcessed(), "every submitted order must be processed exactly once");
        assertEquals(totalOrders, sequencer.currentSequence(), "sequence counter must exactly match total commands - no gaps, no double counting");

        sequencer.shutdown();
        core.shutdown();
        publisher.shutdown();
        wal.close();
    }
}
