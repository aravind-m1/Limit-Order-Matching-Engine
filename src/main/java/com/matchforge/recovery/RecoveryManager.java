package com.matchforge.recovery;

import com.matchforge.command.Command;
import com.matchforge.engine.MatchingCore;
import com.matchforge.persistence.SnapshotManager;
import com.matchforge.persistence.WalReader;

import java.nio.file.Path;
import java.util.List;

public final class RecoveryManager {

    private RecoveryManager() {}

    /**
     * Loads the last snapshot (if any) directly into the core's book, then
     * replays only the WAL records after the snapshot's sequence number.
     * Returns the highest sequence number seen, so a restarted Sequencer
     * can resume numbering from there without ever reusing or skipping one.
     */
    public static long recover(MatchingCore core, Path snapshotPath, Path walPath) {
        long snapshotSeq = SnapshotManager.loadSnapshot(snapshotPath, core.getBook());
        List<Command> records = WalReader.readAll(walPath);
        long maxSeq = snapshotSeq;
        for (Command cmd : records) {
            if (cmd.getSequence() <= snapshotSeq) continue; // already captured in the snapshot
            core.apply(cmd);
            maxSeq = Math.max(maxSeq, cmd.getSequence());
        }
        return maxSeq;
    }
}
