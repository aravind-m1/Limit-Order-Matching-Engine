package com.matchforge;

import com.matchforge.engine.MatchingCore;
import com.matchforge.gateway.Gateway;
import com.matchforge.gateway.NioTcpGateway;
import com.matchforge.marketdata.MarketDataPublisher;
import com.matchforge.matching.PriceTimePriorityStrategy;
import com.matchforge.metrics.MetricsHttpServer;
import com.matchforge.metrics.MetricsRegistry;
import com.matchforge.persistence.WalWriter;
import com.matchforge.risk.RiskFilter;
import com.matchforge.sequencer.Sequencer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/** Smoke test for the NIO TCP gateway and the metrics HTTP endpoint. */
public final class NetworkDemo {
    public static void main(String[] args) throws Exception {
        Path walPath = Files.createTempFile("limit-order-matching-engine-net-wal", ".log");
        MarketDataPublisher publisher = new MarketDataPublisher();
        publisher.start();
        MetricsRegistry metrics = new MetricsRegistry();
        MatchingCore core = new MatchingCore("BTC-USD", new PriceTimePriorityStrategy(), publisher, metrics);
        core.start();
        WalWriter wal = new WalWriter(walPath, true);
        Sequencer sequencer = new Sequencer(wal, 0);
        sequencer.registerCore(core);
        sequencer.start();
        Gateway gateway = new Gateway(sequencer, new RiskFilter(1_000_000, 10_000_000));

        NioTcpGateway tcpGateway = new NioTcpGateway(9631, gateway);
        Thread tcpThread = new Thread(tcpGateway, "nio-tcp-gateway");
        tcpThread.setDaemon(true);
        tcpThread.start();
        Thread.sleep(300);

        MetricsHttpServer httpServer = new MetricsHttpServer(9632, metrics);
        httpServer.start();

        try (Socket socket = new Socket("localhost", 9631)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("NEW,BTC-USD,BUY,100,10,alice");
            System.out.println("TCP response: " + in.readLine());
            out.println("NEW,BTC-USD,SELL,100,4,carol");
            System.out.println("TCP response: " + in.readLine());
            out.println("CANCEL,BTC-USD,999,alice");
            System.out.println("TCP response: " + in.readLine());
        }

        Thread.sleep(300);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:9632/metrics")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println();
        System.out.println("--- GET /metrics ---");
        System.out.println(response.body());

        httpServer.stop();
        tcpGateway.stop();
        sequencer.shutdown();
        core.shutdown();
        publisher.shutdown();
        wal.close();
        System.out.println("Network smoke test complete.");
    }
}
