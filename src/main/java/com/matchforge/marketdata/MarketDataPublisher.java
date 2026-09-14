package com.matchforge.marketdata;

import com.matchforge.model.Trade;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fans out trade and book-update events to subscribers on a dedicated
 * background thread, off the matching hot path - a slow subscriber can
 * never stall the matching core (Observer / pub-sub pattern).
 */
public final class MarketDataPublisher implements Runnable {

    private sealed interface Event permits TradeEvent, BookEvent {}
    private record TradeEvent(Trade trade) implements Event {}
    private record BookEvent(String symbol, Long bestBid, Long bestAsk) implements Event {}

    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
    private final List<MarketDataListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    public MarketDataPublisher() {
        this.thread = new Thread(this, "market-data-publisher");
        this.thread.setDaemon(true);
    }

    public void start() { thread.start(); }

    public void shutdown() {
        running.set(false);
        thread.interrupt();
    }

    public void addListener(MarketDataListener listener) { listeners.add(listener); }

    public void publishTrade(Trade trade) { queue.add(new TradeEvent(trade)); }

    public void publishBookUpdate(String symbol, Long bestBid, Long bestAsk) {
        queue.add(new BookEvent(symbol, bestBid, bestAsk));
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Event e = queue.take();
                if (e instanceof TradeEvent te) {
                    for (MarketDataListener l : listeners) l.onTrade(te.trade());
                } else if (e instanceof BookEvent be) {
                    for (MarketDataListener l : listeners) l.onBookUpdate(be.symbol(), be.bestBid(), be.bestAsk());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
