package com.matchforge.tests;

import com.matchforge.book.OrderBook;
import com.matchforge.matching.PriceTimePriorityStrategy;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.model.Trade;
import com.matchforge.testharness.Test;

import java.util.List;

import static com.matchforge.testharness.Assertions.*;

public class MatchingLogicTest {

    @Test
    public void fullFillAtRestingPrice() {
        OrderBook book = new OrderBook("BTC-USD");
        PriceTimePriorityStrategy strategy = new PriceTimePriorityStrategy();

        Order restingSell = Order.builder().clientId("maker").symbol("BTC-USD").side(Side.SELL).price(100).quantity(10).build();
        book.addRestingOrder(restingSell);

        Order incomingBuy = Order.builder().clientId("taker").symbol("BTC-USD").side(Side.BUY).price(100).quantity(10).build();
        List<Trade> trades = strategy.match(book, incomingBuy, 1);

        assertEquals(1, trades.size(), "one trade should be generated");
        assertEquals(100L, trades.get(0).getPrice(), "trade price should be the resting order's price");
        assertEquals(10L, trades.get(0).getQuantity(), "full quantity should trade");
        assertEquals(0L, incomingBuy.getRemainingQuantity(), "incoming order should be fully filled");
        assertEquals(0, book.restingOrderCount(), "resting order should be fully consumed and removed");
    }

    @Test
    public void partialFillLeavesRemainderResting() {
        OrderBook book = new OrderBook("BTC-USD");
        PriceTimePriorityStrategy strategy = new PriceTimePriorityStrategy();

        Order restingSell = Order.builder().clientId("maker").symbol("BTC-USD").side(Side.SELL).price(100).quantity(4).build();
        book.addRestingOrder(restingSell);

        Order incomingBuy = Order.builder().clientId("taker").symbol("BTC-USD").side(Side.BUY).price(100).quantity(10).build();
        List<Trade> trades = strategy.match(book, incomingBuy, 1);

        assertEquals(1, trades.size(), "one trade for the available resting quantity");
        assertEquals(4L, trades.get(0).getQuantity(), "trade quantity limited by resting order size");
        assertEquals(6L, incomingBuy.getRemainingQuantity(), "6 units should remain unfilled on the incoming order");
    }

    @Test
    public void fifoTimePriorityAtSamePriceLevel() {
        OrderBook book = new OrderBook("BTC-USD");
        PriceTimePriorityStrategy strategy = new PriceTimePriorityStrategy();

        Order firstSell = Order.builder().clientId("maker1").symbol("BTC-USD").side(Side.SELL).price(100).quantity(5).build();
        Order secondSell = Order.builder().clientId("maker2").symbol("BTC-USD").side(Side.SELL).price(100).quantity(5).build();
        book.addRestingOrder(firstSell);
        book.addRestingOrder(secondSell);

        Order incomingBuy = Order.builder().clientId("taker").symbol("BTC-USD").side(Side.BUY).price(100).quantity(5).build();
        List<Trade> trades = strategy.match(book, incomingBuy, 1);

        assertEquals(1, trades.size(), "should match exactly one resting order for 5 units");
        assertEquals(firstSell.getOrderId(), trades.get(0).getSellOrderId(), "earlier resting order must be matched first (FIFO)");
    }

    @Test
    public void selfTradePreventionSkipsSameClient() {
        OrderBook book = new OrderBook("BTC-USD");
        PriceTimePriorityStrategy strategy = new PriceTimePriorityStrategy();

        Order restingSell = Order.builder().clientId("same-client").symbol("BTC-USD").side(Side.SELL).price(100).quantity(5).build();
        book.addRestingOrder(restingSell);

        Order incomingBuy = Order.builder().clientId("same-client").symbol("BTC-USD").side(Side.BUY).price(100).quantity(5).build();
        List<Trade> trades = strategy.match(book, incomingBuy, 1);

        assertEquals(0, trades.size(), "no trade should be generated against the same client's own resting order");
        assertEquals(5L, incomingBuy.getRemainingQuantity(), "incoming order should remain fully unfilled");
    }
}
