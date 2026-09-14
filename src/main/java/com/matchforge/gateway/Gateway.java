package com.matchforge.gateway;

import com.matchforge.command.CancelCommand;
import com.matchforge.command.ModifyCommand;
import com.matchforge.command.NewOrderCommand;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.risk.RiskFilter;
import com.matchforge.sequencer.Sequencer;

/** In-process client-facing API: validates via the risk filter, then hands commands to the Sequencer. */
public final class Gateway {

    private final Sequencer sequencer;
    private final RiskFilter riskFilter;

    public Gateway(Sequencer sequencer, RiskFilter riskFilter) {
        this.sequencer = sequencer;
        this.riskFilter = riskFilter;
    }

    public Order submitNewOrder(String clientId, String symbol, Side side, long price, long quantity) {
        Order order = Order.builder()
                .clientId(clientId)
                .symbol(symbol)
                .side(side)
                .price(price)
                .quantity(quantity)
                .build();

        riskFilter.check(order); // throws RiskRejectionException if rejected

        sequencer.enqueue(new NewOrderCommand(symbol, order));
        return order;
    }

    public void cancelOrder(String clientId, String symbol, long orderId) {
        sequencer.enqueue(new CancelCommand(symbol, orderId, clientId));
    }

    public void modifyOrder(String clientId, String symbol, long orderId, long newPrice, long newQuantity) {
        sequencer.enqueue(new ModifyCommand(symbol, orderId, clientId, newPrice, newQuantity));
    }
}
