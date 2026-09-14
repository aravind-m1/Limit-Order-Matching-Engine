package com.matchforge.matching;

import com.matchforge.book.OrderBook;
import com.matchforge.model.Order;
import com.matchforge.model.Trade;

import java.util.List;

/**
 * Strategy pattern: pluggable matching algorithms. The MatchingCore,
 * Sequencer, and persistence layers never need to change to add a new
 * matching strategy (e.g. pro-rata) - only a new implementation of this
 * interface is required.
 */
public interface MatchingStrategy {
    /**
     * Attempts to match the incoming order against the resting book.
     * Mutates the book (removing/reducing resting orders) and returns the
     * trades generated. If the incoming order has remaining quantity after
     * this call, the caller is responsible for resting it on the book.
     */
    List<Trade> match(OrderBook book, Order incoming, long sequence);
}
