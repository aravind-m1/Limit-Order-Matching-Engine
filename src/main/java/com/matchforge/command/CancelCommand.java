package com.matchforge.command;

public final class CancelCommand extends Command {
    private final String symbol;
    private final long orderId;
    private final String clientId;

    public CancelCommand(String symbol, long orderId, String clientId) {
        this.symbol = symbol;
        this.orderId = orderId;
        this.clientId = clientId;
    }

    public long getOrderId() { return orderId; }
    public String getClientId() { return clientId; }

    @Override
    public String getSymbol() { return symbol; }

    @Override
    public CommandType getType() { return CommandType.CANCEL; }
}
