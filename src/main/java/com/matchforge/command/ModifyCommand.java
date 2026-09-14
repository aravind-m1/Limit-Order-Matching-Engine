package com.matchforge.command;

public final class ModifyCommand extends Command {
    private final String symbol;
    private final long orderId;
    private final String clientId;
    private final long newPrice;
    private final long newQuantity;

    public ModifyCommand(String symbol, long orderId, String clientId, long newPrice, long newQuantity) {
        this.symbol = symbol;
        this.orderId = orderId;
        this.clientId = clientId;
        this.newPrice = newPrice;
        this.newQuantity = newQuantity;
    }

    public long getOrderId() { return orderId; }
    public String getClientId() { return clientId; }
    public long getNewPrice() { return newPrice; }
    public long getNewQuantity() { return newQuantity; }

    @Override
    public String getSymbol() { return symbol; }

    @Override
    public CommandType getType() { return CommandType.MODIFY; }
}
