package com.matchforge.persistence;

import com.matchforge.command.Command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;

/**
 * Append-only write-ahead log. Every record on disk is laid out as:
 *   [4 bytes payload length][8 bytes sequence][payload bytes][8 bytes CRC32 checksum]
 * The checksum covers the sequence + payload, so a torn/partial write at
 * the tail (from a process crash mid-write) is detected and discarded on
 * recovery instead of silently corrupting replay.
 */
public final class WalWriter implements AutoCloseable {

    private final FileChannel channel;
    private final boolean syncEveryWrite;

    public WalWriter(Path path, boolean syncEveryWrite) {
        this.syncEveryWrite = syncEveryWrite;
        try {
            this.channel = FileChannel.open(path,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void append(Command cmd) {
        byte[] payload = CommandCodec.encode(cmd);

        CRC32 crc = new CRC32();
        ByteBuffer seqBuf = ByteBuffer.allocate(8).putLong(cmd.getSequence());
        seqBuf.flip();
        crc.update(seqBuf);
        crc.update(payload);

        ByteBuffer buf = ByteBuffer.allocate(4 + 8 + payload.length + 8);
        buf.putInt(payload.length);
        buf.putLong(cmd.getSequence());
        buf.put(payload);
        buf.putLong(crc.getValue());
        buf.flip();

        try {
            while (buf.hasRemaining()) {
                channel.write(buf);
            }
            if (syncEveryWrite) {
                channel.force(false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void flush() {
        try {
            channel.force(false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            channel.force(true);
            channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
