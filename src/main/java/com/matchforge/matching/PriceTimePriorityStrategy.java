package com.matchforge.matching;

import com.matchforge.book.OrderBook;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.model.Trade;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Continuous double-auction matching: price priority first, then time
 * priority (FIFO) within a price level. Includes self-trade prevention -
 * an incoming order never matches against a resting order from the same
 * client, since that would generate a wash trade.
 *
 * Simplification: if every resting order at the best contra price belongs
 * to the same client as the incoming order (fully self-trade-blocked),
 * this strategy stops rather than reaching through to a worse price level,
 * matching how most real venues behave (price-time priority is not skipped
 * to satisfy a taker).
 */
public final class PriceTimePriorityStrategy implements MatchingStrategy {

    @Override
    public List<Trade> match(OrderBook book, Order incoming, long sequence) {
        List<Trade> trades = new ArrayList<>();
        Side contraSide = incoming.getSide().opposite();

        while (incoming.getRemainingQuantity() > 0) {
            Long bestContraPrice = book.bestPrice(contraSide);
            if (bestContraPrice == null) break;

            boolean crosses = incoming.getSide() == Side.BUY
                    ? incoming.getPrice() >= bestContraPrice
                    : incoming.getPrice() <= bestContraPrice;
            if (!crosses) break;

            Deque<Order> level = book.ordersAt(contraSide, bestContraPrice);
            if (level == null || level.isEmpty()) {
                book.pruneIfEmpty(contraSide, bestContraPrice);
                continue;
            }

            boolean matchedAnyAtThisLevel = false;
            Iterator<Order> it = level.iterator();
            while (it.hasNext() && incoming.getRemainingQuantity() > 0) {
                Order resting = it.next();

                if (resting.getClientId().equals(incoming.getClientId())) {
                    continue; // self-trade prevention
                }

                long tradedQty = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());
                long tradePrice = resting.getPrice(); // resting order sets the trade price

                resting.reduceRemaining(tradedQty);
                incoming.reduceRemaining(tradedQty);
                matchedAnyAtThisLevel = true;

                long buyId = incoming.getSide() == Side.BUY ? incoming.getOrderId() : resting.getOrderId();
                long sellId = incoming.getSide() == Side.SELL ? incoming.getOrderId() : resting.getOrderId();
                trades.add(new Trade(book.getSymbol(), buyId, sellId, tradePrice, tradedQty, sequence));

                if (resting.getRemainingQuantity() == 0) {
                    it.remove();
                    book.removeFromIndex(resting.getOrderId());
                }
            }

            book.pruneIfEmpty(contraSide, bestContraPrice);

            if (!matchedAnyAtThisLevel) {
                break; // entire level was self-trade-blocked; cannot progress further
            }
        }
        return trades;
    }
}
