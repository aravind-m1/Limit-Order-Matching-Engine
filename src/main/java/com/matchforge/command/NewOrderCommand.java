package com.matchforge.command;

import com.matchforge.model.Order;

public final class NewOrderCommand extends Command {
    private final String symbol;
    private final Order order;

    public NewOrderCommand(String symbol, Order order) {
        this.symbol = symbol;
        this.order = order;
    }

    public Order getOrder() { return order; }

    @Override
    public String getSymbol() { return symbol; }

    @Override
    public CommandType getType() { return CommandType.NEW_ORDER; }
}
