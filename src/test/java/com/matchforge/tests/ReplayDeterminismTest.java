package com.matchforge.tests;

import com.matchforge.command.Command;
import com.matchforge.command.NewOrderCommand;
import com.matchforge.engine.MatchingCore;
import com.matchforge.marketdata.MarketDataPublisher;
import com.matchforge.matching.PriceTimePriorityStrategy;
import com.matchforge.metrics.MetricsRegistry;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.testharness.Test;

import java.util.ArrayList;
import java.util.List;

import static com.matchforge.testharness.Assertions.*;

/**
 * Proves the core is deterministic: feeding the exact same sequenced
 * command stream into two fresh matching cores must produce byte-identical
 * resulting state. This is only possible because the core is
 * single-threaded per shard - it is a direct payoff of that design choice.
 */
public class ReplayDeterminismTest {

    private List<Command> buildFixedCommandSequence() {
        List<Command> commands = new ArrayList<>();
        long seq = 1;
        long[][] spec = {
            // side(0=BUY,1=SELL), price, qty, clientIdSuffix
            {0, 100, 5, 1}, {1, 100, 3, 2}, {0, 101, 4, 3}, {1, 99, 6, 4}, {0, 99, 2, 5}
        };
        for (long[] row : spec) {
            Order o = Order.builder()
                    .clientId("client-" + row[3])
                    .symbol("BTC-USD")
                    .side(row[0] == 0 ? Side.BUY : Side.SELL)
                    .price(row[1])
                    .quantity(row[2])
                    .build();
            Command cmd = new NewOrderCommand("BTC-USD", o);
            cmd.setSequence(seq++);
            commands.add(cmd);
        }
        return commands;
    }

    @Test
    public void sameInputSequenceProducesIdenticalOutput() {
        List<Command> runA = buildFixedCommandSequence();
        List<Command> runB = buildFixedCommandSequence();

        String resultA = runOnFreshCore(runA);
        String resultB = runOnFreshCore(runB);

        assertEqualsObj(resultA, resultB, "identical input sequence must produce byte-identical output, since the core is single-threaded per shard");
    }

    private String runOnFreshCore(List<Command> commands) {
        MarketDataPublisher publisher = new MarketDataPublisher(); // not started - only direct apply() is used
        MetricsRegistry metrics = new MetricsRegistry();
        MatchingCore core = new MatchingCore("BTC-USD", new PriceTimePriorityStrategy(), publisher, metrics);
        for (Command cmd : commands) {
            core.apply(cmd);
        }
        return "resting=" + core.getBook().restingOrderCount()
                + ",bestBid=" + core.getBook().bestPrice(Side.BUY)
                + ",bestAsk=" + core.getBook().bestPrice(Side.SELL)
                + ",trades=" + metrics.getTradesExecuted();
    }
}
