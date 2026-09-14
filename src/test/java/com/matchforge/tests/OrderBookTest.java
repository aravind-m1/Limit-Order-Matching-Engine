package com.matchforge.tests;

import com.matchforge.book.OrderBook;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.testharness.Test;

import static com.matchforge.testharness.Assertions.*;

public class OrderBookTest {

    @Test
    public void addAndRetrieveBestPrices() {
        OrderBook book = new OrderBook("BTC-USD");
        Order buy1 = Order.builder().clientId("c1").symbol("BTC-USD").side(Side.BUY).price(100).quantity(5).build();
        Order buy2 = Order.builder().clientId("c1").symbol("BTC-USD").side(Side.BUY).price(105).quantity(5).build();
        Order sell1 = Order.builder().clientId("c2").symbol("BTC-USD").side(Side.SELL).price(110).quantity(5).build();

        book.addRestingOrder(buy1);
        book.addRestingOrder(buy2);
        book.addRestingOrder(sell1);

        assertEqualsObj(105L, book.bestPrice(Side.BUY), "best bid should be the highest buy price");
        assertEqualsObj(110L, book.bestPrice(Side.SELL), "best ask should be the lowest sell price");
        assertEquals(3, book.restingOrderCount(), "all three resting orders should be indexed");
    }

    @Test
    public void cancelRemovesFromBothLevelAndIndex() {
        OrderBook book = new OrderBook("BTC-USD");
        Order buy1 = Order.builder().clientId("c1").symbol("BTC-USD").side(Side.BUY).price(100).quantity(5).build();
        book.addRestingOrder(buy1);

        Order removed = book.removeOrder(buy1.getOrderId());
        assertNotNull(removed, "removeOrder should return the removed order");
        assertEquals(0, book.restingOrderCount(), "book should be empty after removing the only order");
        assertEqualsObj(null, book.bestPrice(Side.BUY), "best bid should be null with no resting bids");
    }
}
