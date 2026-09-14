package com.matchforge.engine;

import com.matchforge.book.OrderBook;
import com.matchforge.command.CancelCommand;
import com.matchforge.command.Command;
import com.matchforge.command.ModifyCommand;
import com.matchforge.command.NewOrderCommand;
import com.matchforge.exceptions.InvalidOrderException;
import com.matchforge.marketdata.MarketDataPublisher;
import com.matchforge.matching.MatchingStrategy;
import com.matchforge.metrics.MetricsRegistry;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.model.Trade;
import com.matchforge.persistence.WalWriter;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns exactly one OrderBook and runs on a single dedicated thread. This
 * is the "single writer" for its symbol: no other thread ever mutates
 * this book, so no locking is needed inside the hot matching path. Other
 * symbols run on their own MatchingCore/thread, so throughput scales by
 * sharding across symbols rather than by adding locks to a shared book.
 */
public final class MatchingCore implements Runnable {

    private final String symbol;
    private final OrderBook book;
    private final MatchingStrategy strategy;
    private final BlockingQueue<Command> inbox = new LinkedBlockingQueue<>();
    private final MarketDataPublisher publisher;
    private final MetricsRegistry metrics;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    public MatchingCore(String symbol, MatchingStrategy strategy, MarketDataPublisher publisher, MetricsRegistry metrics) {
        this.symbol = symbol;
        this.book = new OrderBook(symbol);
        this.strategy = strategy;
        this.publisher = publisher;
        this.metrics = metrics;
        this.thread = new Thread(this, "matching-core-" + symbol);
        this.thread.setDaemon(true);
    }

    public String getSymbol() { return symbol; }
    public OrderBook getBook() { return book; }

    public void start() { thread.start(); }

    /** Called by the Sequencer thread only - this is the sole producer into this core's inbox. */
    public void submit(Command command) {
        inbox.add(command);
    }

    public void shutdown() {
        running.set(false);
        thread.interrupt();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Command cmd = inbox.take();
                long startNanos = System.nanoTime();
                apply(cmd);
                metrics.recordMatchLatency(System.nanoTime() - startNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Applies a single already-sequenced command directly. Used by the
     * matching thread in normal operation, and also called synchronously
     * (no thread involved) by the RecoveryManager during WAL replay and by
     * deterministic-replay tests.
     */
    public void apply(Command cmd) {
        if (cmd instanceof NewOrderCommand nc) {
            applyNewOrder(nc.getOrder(), cmd.getSequence());
        } else if (cmd instanceof CancelCommand cc) {
            applyCancel(cc);
        } else if (cmd instanceof ModifyCommand mc) {
            applyModify(mc);
        } else {
            throw new InvalidOrderException("Unknown command type: " + cmd.getType());
        }
    }

    private void applyNewOrder(Order order, long sequence) {
        order.setSequence(sequence);
        List<Trade> trades = strategy.match(book, order, sequence);
        for (Trade t : trades) {
            metrics.incrementTrades();
            publisher.publishTrade(t);
        }
        if (order.getRemainingQuantity() > 0) {
            book.addRestingOrder(order);
        }
        metrics.incrementOrders();
        publisher.publishBookUpdate(symbol, book.bestPrice(Side.BUY), book.bestPrice(Side.SELL));
    }

    private void applyCancel(CancelCommand cmd) {
        Order existing = book.getOrder(cmd.getOrderId());
        if (existing == null) return; // already filled/canceled/unknown - idempotent no-op
        if (!existing.getClientId().equals(cmd.getClientId())) return; // not the owner
        book.removeOrder(cmd.getOrderId());
        existing.cancel();
    }

    private void applyModify(ModifyCommand cmd) {
        Order existing = book.getOrder(cmd.getOrderId());
        if (existing == null || !existing.getClientId().equals(cmd.getClientId())) return;

        // Cancel-replace semantics: modifying loses time priority, matching real exchange behavior.
        book.removeOrder(cmd.getOrderId());
        existing.cancel();
        Order replacement = Order.builder()
                .clientId(cmd.getClientId())
                .symbol(cmd.getSymbol())
                .side(existing.getSide())
                .price(cmd.getNewPrice())
                .quantity(cmd.getNewQuantity())
                .build();
        applyNewOrder(replacement, cmd.getSequence());
    }
}
