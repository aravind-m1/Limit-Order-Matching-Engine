package com.matchforge.model;

import java.util.concurrent.atomic.AtomicLong;

public final class Trade {
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long tradeId;
    private final String symbol;
    private final long buyOrderId;
    private final long sellOrderId;
    private final long price;
    private final long quantity;
    private final long sequence;
    private final long timestampNanos;

    public Trade(String symbol, long buyOrderId, long sellOrderId, long price, long quantity, long sequence) {
        this.tradeId = ID_GEN.getAndIncrement();
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.sequence = sequence;
        this.timestampNanos = System.nanoTime();
    }

    public long getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public long getBuyOrderId() { return buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public long getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public long getSequence() { return sequence; }
    public long getTimestampNanos() { return timestampNanos; }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, %s, buy=%d, sell=%d, price=%d, qty=%d, seq=%d}",
                tradeId, symbol, buyOrderId, sellOrderId, price, quantity, sequence);
    }
}
