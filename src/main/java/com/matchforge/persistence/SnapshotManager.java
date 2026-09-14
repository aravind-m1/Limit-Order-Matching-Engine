package com.matchforge.persistence;

import com.matchforge.book.OrderBook;
import com.matchforge.model.Order;
import com.matchforge.model.Side;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Periodically dumps full order-book state (all resting orders + the
 * sequence number up to which they reflect) so that WAL replay on restart
 * only needs to cover the tail since the last snapshot - bounding recovery
 * time regardless of how long the engine has been running.
 */
public final class SnapshotManager {

    private SnapshotManager() {}

    public static void writeSnapshot(Path path, long lastSequence, OrderBook book) {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
            out.writeLong(lastSequence);
            List<Order> resting = new ArrayList<>();
            collect(book.getBidsView(), resting);
            collect(book.getAsksView(), resting);
            out.writeInt(resting.size());
            for (Order o : resting) {
                out.writeLong(o.getOrderId());
                out.writeUTF(o.getClientId());
                out.writeUTF(o.getSymbol());
                out.writeByte(o.getSide() == Side.BUY ? 0 : 1);
                out.writeLong(o.getPrice());
                out.writeLong(o.getRemainingQuantity());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void collect(Map<Long, Deque<Order>> side, List<Order> out) {
        for (Deque<Order> level : side.values()) {
            out.addAll(level);
        }
    }

    /** Returns the snapshot's last-included sequence number, or 0 if no snapshot exists yet. */
    public static long loadSnapshot(Path path, OrderBook book) {
        if (!path.toFile().exists() || path.toFile().length() == 0) return 0L;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path.toFile())))) {
            long lastSequence = in.readLong();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long orderId = in.readLong();
                String clientId = in.readUTF();
                String symbol = in.readUTF();
                Side side = in.readByte() == 0 ? Side.BUY : Side.SELL;
                long price = in.readLong();
                long qty = in.readLong();
                Order order = Order.builder()
                        .orderId(orderId).clientId(clientId).symbol(symbol)
                        .side(side).price(price).quantity(qty).build();
                book.addRestingOrder(order);
            }
            return lastSequence;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
