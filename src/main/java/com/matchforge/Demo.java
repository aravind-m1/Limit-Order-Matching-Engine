package com.matchforge;

import com.matchforge.engine.MatchingCore;
import com.matchforge.gateway.Gateway;
import com.matchforge.marketdata.MarketDataPublisher;
import com.matchforge.matching.PriceTimePriorityStrategy;
import com.matchforge.metrics.MetricsRegistry;
import com.matchforge.model.Side;
import com.matchforge.persistence.SnapshotManager;
import com.matchforge.persistence.WalWriter;
import com.matchforge.recovery.RecoveryManager;
import com.matchforge.risk.RiskFilter;
import com.matchforge.sequencer.Sequencer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end demo: wires the whole engine together, submits orders that
 * generate trades and partial fills, takes a snapshot, then simulates a
 * process crash and shows the engine reconstructing identical state from
 * the snapshot + WAL tail alone.
 */
public final class Demo {

    public static void main(String[] args) throws Exception {
        Path walPath = Files.createTempFile("matchforge-demo-wal", ".log");
        Path snapshotPath = Files.createTempFile("matchforge-demo-snapshot", ".bin");

        System.out.println("=== MatchForge Demo ===");
        System.out.println("WAL file: " + walPath);
        System.out.println();

        MarketDataPublisher publisher = new MarketDataPublisher();
        publisher.addListener(new ConsoleMarketDataListener());
        publisher.start();

        MetricsRegistry metrics = new MetricsRegistry();
        MatchingCore core = new MatchingCore("BTC-USD", new PriceTimePriorityStrategy(), publisher, metrics);
        core.start();

        WalWriter wal = new WalWriter(walPath, true);
        Sequencer sequencer = new Sequencer(wal, 0);
        sequencer.registerCore(core);
        sequencer.start();

        RiskFilter riskFilter = new RiskFilter(1_000_000, 10_000_000);
        Gateway gateway = new Gateway(sequencer, riskFilter);

        System.out.println("--- Submitting orders ---");
        gateway.submitNewOrder("alice", "BTC-USD", Side.BUY, 100, 10);
        gateway.submitNewOrder("bob", "BTC-USD", Side.SELL, 105, 5);
        gateway.submitNewOrder("carol", "BTC-USD", Side.SELL, 100, 4); // crosses alice's bid -> trade
        gateway.submitNewOrder("dave", "BTC-USD", Side.BUY, 106, 5);   // crosses bob's ask -> trade

        TimeUnit.MILLISECONDS.sleep(300); // let the async pipeline drain so demo output prints in order

        System.out.println();
        System.out.println("--- Book state ---");
        System.out.println("Best bid: " + core.getBook().bestPrice(Side.BUY));
        System.out.println("Best ask: " + core.getBook().bestPrice(Side.SELL));
        System.out.println("Resting orders: " + core.getBook().restingOrderCount());

        System.out.println();
        System.out.println("--- Metrics ---");
        System.out.println(metrics.renderText());

        System.out.println("--- Taking a snapshot, then simulating a crash + recovery ---");
        SnapshotManager.writeSnapshot(snapshotPath, sequencer.currentSequence(), core.getBook());
        wal.close(); // simulate the process stopping

        MatchingCore recoveredCore = new MatchingCore("BTC-USD", new PriceTimePriorityStrategy(), publisher, new MetricsRegistry());
        long resumeSeq = RecoveryManager.recover(recoveredCore, snapshotPath, walPath);

        System.out.println("Recovered resting orders: " + recoveredCore.getBook().restingOrderCount());
        System.out.println("Recovered best bid: " + recoveredCore.getBook().bestPrice(Side.BUY));
        System.out.println("Recovered best ask: " + recoveredCore.getBook().bestPrice(Side.SELL));
        System.out.println("Sequencer would resume numbering from: " + (resumeSeq + 1));

        sequencer.shutdown();
        core.shutdown();
        publisher.shutdown();
        System.out.println();
        System.out.println("=== Demo complete ===");
    }
}
