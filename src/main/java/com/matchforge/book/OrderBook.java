package com.matchforge.book;

import com.matchforge.model.Order;
import com.matchforge.model.Side;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owns the resting orders for a single symbol. NOT thread-safe by design:
 * it is intended to be owned and mutated exclusively by a single
 * MatchingCore thread (the single-writer principle) - no other thread
 * ever touches this object, so no locking is needed inside it.
 */
public final class OrderBook {

    private final String symbol;

    // Bids: highest price first (best bid = highest buy price)
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    // Asks: lowest price first (best ask = lowest sell price)
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();

    // Side-index: orderId -> Order, gives O(1) cancel/lookup instead of an O(n) scan across price levels.
    private final Map<Long, Order> orderIndex = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() { return symbol; }

    private TreeMap<Long, Deque<Order>> bookSide(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    public void addRestingOrder(Order order) {
        bookSide(order.getSide())
                .computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>())
                .addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    public Order removeOrder(long orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) return null;
        Deque<Order> level = bookSide(order.getSide()).get(order.getPrice());
        if (level != null) {
            level.remove(order);
            if (level.isEmpty()) {
                bookSide(order.getSide()).remove(order.getPrice());
            }
        }
        return order;
    }

    public Order getOrder(long orderId) {
        return orderIndex.get(orderId);
    }

    public Long bestPrice(Side side) {
        Map.Entry<Long, Deque<Order>> entry = bookSide(side).firstEntry();
        return entry == null ? null : entry.getKey();
    }

    public Deque<Order> ordersAt(Side side, long price) {
        return bookSide(side).get(price);
    }

    /** Removes a price level once it has been fully drained during matching. */
    public void pruneIfEmpty(Side side, long price) {
        Deque<Order> level = bookSide(side).get(price);
        if (level != null && level.isEmpty()) {
            bookSide(side).remove(price);
        }
    }

    public void removeFromIndex(long orderId) {
        orderIndex.remove(orderId);
    }

    public int restingOrderCount() {
        return orderIndex.size();
    }

    public TreeMap<Long, Deque<Order>> getBidsView() { return bids; }
    public TreeMap<Long, Deque<Order>> getAsksView() { return asks; }
}
