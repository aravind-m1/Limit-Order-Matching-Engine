package com.matchforge.gateway;

import com.matchforge.exceptions.RiskRejectionException;
import com.matchforge.model.Order;
import com.matchforge.model.Side;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * Minimal NIO/Selector-based TCP gateway using a simple line protocol:
 *   NEW,<symbol>,<BUY|SELL>,<price>,<qty>,<clientId>\n
 *   CANCEL,<symbol>,<orderId>,<clientId>\n
 * Demonstrates handling many concurrent client connections on a single
 * thread via a Selector, instead of one thread per connection.
 */
public final class NioTcpGateway implements Runnable {

    private final int port;
    private final Gateway gateway;
    private volatile boolean running = true;
    private Selector selector;

    public NioTcpGateway(int port, Gateway gateway) {
        this.port = port;
        this.gateway = gateway;
    }

    public void stop() {
        running = false;
        if (selector != null) selector.wakeup();
    }

    @Override
    public void run() {
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (running) {
                selector.select(500);
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) accept(serverChannel);
                    else if (key.isReadable()) read(key);
                }
            }
        } catch (IOException e) {
            if (running) throw new RuntimeException(e);
        }
    }

    private void accept(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) return;
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(4096));
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        int n = client.read(buf);
        if (n == -1) {
            client.close();
            return;
        }
        buf.flip();
        String chunk = StandardCharsets.UTF_8.decode(buf).toString();
        buf.clear();
        for (String line : chunk.split("\n")) {
            if (!line.isBlank()) handleLine(client, line.trim());
        }
    }

    private void handleLine(SocketChannel client, String line) throws IOException {
        String response;
        try {
            String[] parts = line.split(",");
            switch (parts[0]) {
                case "NEW" -> {
                    String symbol = parts[1];
                    Side side = Side.valueOf(parts[2]);
                    long price = Long.parseLong(parts[3]);
                    long qty = Long.parseLong(parts[4]);
                    String clientId = parts[5];
                    Order order = gateway.submitNewOrder(clientId, symbol, side, price, qty);
                    response = "ACK,orderId=" + order.getOrderId() + "\n";
                }
                case "CANCEL" -> {
                    String symbol = parts[1];
                    long orderId = Long.parseLong(parts[2]);
                    String clientId = parts[3];
                    gateway.cancelOrder(clientId, symbol, orderId);
                    response = "ACK,cancel=" + orderId + "\n";
                }
                default -> response = "REJECT,unknown_command\n";
            }
        } catch (RiskRejectionException e) {
            response = "REJECT," + e.getMessage() + "\n";
        } catch (Exception e) {
            response = "REJECT,malformed_message\n";
        }
        client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
    }
}
