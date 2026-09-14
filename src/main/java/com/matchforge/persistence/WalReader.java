package com.matchforge.persistence;

import com.matchforge.command.Command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Reads WAL records sequentially, validating each checksum. Stops (without
 * throwing) at the first incomplete or corrupt record, since that is
 * exactly what a crash mid-write looks like on disk - the correct recovery
 * behavior is to silently discard that trailing record, not fail startup
 * or trust corrupted data.
 */
public final class WalReader {

    private WalReader() {}

    public static List<Command> readAll(Path path) {
        List<Command> commands = new ArrayList<>();
        if (!Files.exists(path)) return commands;

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            long position = 0;

            while (position < size) {
                ByteBuffer header = ByteBuffer.allocate(12); // 4 (length) + 8 (sequence)
                channel.position(position);
                int headerRead = readFully(channel, header);
                if (headerRead < 12) break; // incomplete header at tail -> stop, discard

                header.flip();
                int payloadLength = header.getInt();
                long sequence = header.getLong();

                long recordTotalLen = 12L + payloadLength + 8L;
                if (position + recordTotalLen > size) break; // incomplete record at tail -> stop, discard

                ByteBuffer body = ByteBuffer.allocate(payloadLength + 8);
                channel.position(position + 12);
                int bodyRead = readFully(channel, body);
                if (bodyRead < payloadLength + 8) break;
                body.flip();

                byte[] payload = new byte[payloadLength];
                body.get(payload);
                long storedChecksum = body.getLong();

                CRC32 crc = new CRC32();
                ByteBuffer seqBuf = ByteBuffer.allocate(8).putLong(sequence);
                seqBuf.flip();
                crc.update(seqBuf);
                crc.update(payload);

                if (crc.getValue() != storedChecksum) {
                    break; // corrupt tail record -> stop, discard rather than trust bad data
                }

                Command cmd = CommandCodec.decode(payload);
                cmd.setSequence(sequence);
                commands.add(cmd);

                position += recordTotalLen;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return commands;
    }

    private static int readFully(FileChannel channel, ByteBuffer buf) throws IOException {
        int total = 0;
        while (buf.hasRemaining()) {
            int n = channel.read(buf);
            if (n < 0) break;
            total += n;
        }
        return total;
    }
}
