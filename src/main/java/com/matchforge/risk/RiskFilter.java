package com.matchforge.risk;

import com.matchforge.exceptions.RiskRejectionException;
import com.matchforge.model.Order;

/** Pre-trade risk checks applied before an order is allowed to enter the sequencer. */
public final class RiskFilter {

    private final long maxOrderQuantity;
    private final long maxPrice;

    public RiskFilter(long maxOrderQuantity, long maxPrice) {
        this.maxOrderQuantity = maxOrderQuantity;
        this.maxPrice = maxPrice;
    }

    public void check(Order order) {
        if (order.getOriginalQuantity() <= 0) {
            throw new RiskRejectionException("Quantity must be positive");
        }
        if (order.getOriginalQuantity() > maxOrderQuantity) {
            throw new RiskRejectionException("Order quantity " + order.getOriginalQuantity()
                    + " exceeds max allowed " + maxOrderQuantity + " (fat-finger check)");
        }
        if (order.getPrice() <= 0 || order.getPrice() > maxPrice) {
            throw new RiskRejectionException("Order price " + order.getPrice() + " out of bounds");
        }
    }
}
