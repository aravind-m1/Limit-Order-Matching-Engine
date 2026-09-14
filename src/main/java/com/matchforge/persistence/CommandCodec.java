package com.matchforge.persistence;

import com.matchforge.command.CancelCommand;
import com.matchforge.command.Command;
import com.matchforge.command.CommandType;
import com.matchforge.command.ModifyCommand;
import com.matchforge.command.NewOrderCommand;
import com.matchforge.model.Order;
import com.matchforge.model.Side;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Hand-rolled compact binary encoding for WAL records. Deliberately not
 * using Java's default Serializable mechanism, which is verbose, slow,
 * and embeds full class metadata per record - unacceptable overhead for
 * an append-only log written on every single order.
 */
public final class CommandCodec {

    private CommandCodec() {}

    public static byte[] encode(Command cmd) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(cmd.getType().code());
            out.writeUTF(cmd.getSymbol());

            if (cmd instanceof NewOrderCommand nc) {
                Order o = nc.getOrder();
                out.writeLong(o.getOrderId());
                out.writeUTF(o.getClientId());
                out.writeByte(o.getSide() == Side.BUY ? 0 : 1);
                out.writeLong(o.getPrice());
                out.writeLong(o.getOriginalQuantity());
            } else if (cmd instanceof CancelCommand cc) {
                out.writeLong(cc.getOrderId());
                out.writeUTF(cc.getClientId());
            } else if (cmd instanceof ModifyCommand mc) {
                out.writeLong(mc.getOrderId());
                out.writeUTF(mc.getClientId());
                out.writeLong(mc.getNewPrice());
                out.writeLong(mc.getNewQuantity());
            } else {
                throw new IllegalArgumentException("Unknown command type: " + cmd.getClass());
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Command decode(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int typeCode = in.readByte();
            CommandType type = CommandType.fromCode(typeCode);
            String symbol = in.readUTF();

            return switch (type) {
                case NEW_ORDER -> {
                    long orderId = in.readLong();
                    String clientId = in.readUTF();
                    Side side = in.readByte() == 0 ? Side.BUY : Side.SELL;
                    long price = in.readLong();
                    long qty = in.readLong();
                    Order order = Order.builder()
                            .orderId(orderId)
                            .clientId(clientId)
                            .symbol(symbol)
                            .side(side)
                            .price(price)
                            .quantity(qty)
                            .build();
                    yield new NewOrderCommand(symbol, order);
                }
                case CANCEL -> {
                    long orderId = in.readLong();
                    String clientId = in.readUTF();
                    yield new CancelCommand(symbol, orderId, clientId);
                }
                case MODIFY -> {
                    long orderId = in.readLong();
                    String clientId = in.readUTF();
                    long newPrice = in.readLong();
                    long newQty = in.readLong();
                    yield new ModifyCommand(symbol, orderId, clientId, newPrice, newQty);
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
