package com.matchforge.metrics;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Exposes a /metrics text endpoint using the JDK's built-in HTTP server - no external web framework needed. */
public final class MetricsHttpServer {

    private final HttpServer server;

    public MetricsHttpServer(int port, MetricsRegistry registry) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", exchange -> {
            byte[] body = registry.renderText().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(null);
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
}
