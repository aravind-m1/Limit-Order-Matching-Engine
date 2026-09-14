package com.matchforge.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A single order. Mutable remaining-quantity/status fields are only ever
 * touched by the single matching-core thread that owns the book this
 * order lives in, so no internal synchronization is needed here.
 */
public final class Order {
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long orderId;
    private final String clientId;
    private final String symbol;
    private final Side side;
    private final long price;              // integer ticks, never floating point
    private final long originalQuantity;
    private long remainingQuantity;
    private OrderStatus status;
    private long sequence = -1;             // assigned by the Sequencer (or WAL replay)
    private final long entryTimeNanos;

    private Order(Builder b) {
        this.orderId = b.orderId;
        this.clientId = b.clientId;
        this.symbol = b.symbol;
        this.side = b.side;
        this.price = b.price;
        this.originalQuantity = b.quantity;
        this.remainingQuantity = b.quantity;
        this.status = OrderStatus.NEW;
        this.entryTimeNanos = System.nanoTime();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private long orderId = ID_GEN.getAndIncrement();
        private String clientId;
        private String symbol;
        private Side side;
        private long price;
        private long quantity;

        public Builder orderId(long id) { this.orderId = id; return this; }
        public Builder clientId(String c) { this.clientId = c; return this; }
        public Builder symbol(String s) { this.symbol = s; return this; }
        public Builder side(Side s) { this.side = s; return this; }
        public Builder price(long p) { this.price = p; return this; }
        public Builder quantity(long q) { this.quantity = q; return this; }

        public Order build() {
            if (symbol == null || side == null || price <= 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid order fields: symbol/side required, price and quantity must be positive");
            }
            return new Order(this);
        }
    }

    public long getOrderId() { return orderId; }
    public String getClientId() { return clientId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public long getPrice() { return price; }
    public long getOriginalQuantity() { return originalQuantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public long getSequence() { return sequence; }
    public long getEntryTimeNanos() { return entryTimeNanos; }

    public void setSequence(long seq) {
        this.sequence = seq;
    }

    public void reduceRemaining(long qty) {
        if (qty > remainingQuantity) throw new IllegalStateException("Cannot reduce more than remaining quantity");
        remainingQuantity -= qty;
        status = remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        if (status == OrderStatus.FILLED || status == OrderStatus.CANCELED) {
            throw new IllegalStateException("Cannot cancel order in terminal state " + status);
        }
        status = OrderStatus.CANCELED;
    }

    public void reject() { status = OrderStatus.REJECTED; }

    @Override
    public String toString() {
        return String.format("Order{id=%d, client=%s, %s %s %d@%d, remaining=%d, status=%s, seq=%d}",
                orderId, clientId, symbol, side, originalQuantity, price, remainingQuantity, status, sequence);
    }
}
