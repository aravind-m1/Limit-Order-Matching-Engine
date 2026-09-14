package com.matchforge.tests;

import com.matchforge.command.Command;
import com.matchforge.command.NewOrderCommand;
import com.matchforge.model.Order;
import com.matchforge.model.Side;
import com.matchforge.persistence.WalReader;
import com.matchforge.persistence.WalWriter;
import com.matchforge.testharness.Test;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.matchforge.testharness.Assertions.*;

public class WalRecoveryTest {

    @Test
    public void roundTripPreservesAllValidRecords() throws Exception {
        Path path = Files.createTempFile("wal-roundtrip", ".log");
        path.toFile().deleteOnExit();

        try (WalWriter writer = new WalWriter(path, true)) {
            for (int i = 1; i <= 5; i++) {
                Order o = Order.builder().clientId("c1").symbol("BTC-USD").side(Side.BUY).price(100 + i).quantity(i).build();
                NewOrderCommand cmd = new NewOrderCommand("BTC-USD", o);
                cmd.setSequence(i);
                writer.append(cmd);
            }
        }

        List<Command> replayed = WalReader.readAll(path);
        assertEquals(5, replayed.size(), "all 5 valid records should be read back");
        assertEquals(1L, replayed.get(0).getSequence(), "first record sequence should be preserved");
        assertEquals(5L, replayed.get(4).getSequence(), "last record sequence should be preserved");
    }

    @Test
    public void tornTailRecordIsDiscardedNotTrusted() throws Exception {
        Path path = Files.createTempFile("wal-torn", ".log");
        path.toFile().deleteOnExit();

        try (WalWriter writer = new WalWriter(path, true)) {
            for (int i = 1; i <= 3; i++) {
                Order o = Order.builder().clientId("c1").symbol("BTC-USD").side(Side.BUY).price(100 + i).quantity(i).build();
                NewOrderCommand cmd = new NewOrderCommand("BTC-USD", o);
                cmd.setSequence(i);
                writer.append(cmd);
            }
        }

        // Simulate a crash mid-write by truncating the file partway through the last record.
        long fullLength = Files.size(path);
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.setLength(fullLength - 5); // chop off the tail of the last record
        }

        List<Command> replayed = WalReader.readAll(path);
        assertEquals(2, replayed.size(), "the torn trailing record must be discarded, not trusted");
    }
}
