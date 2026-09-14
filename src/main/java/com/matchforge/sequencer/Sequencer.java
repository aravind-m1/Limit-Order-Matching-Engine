package com.matchforge.sequencer;

import com.matchforge.command.Command;
import com.matchforge.engine.MatchingCore;
import com.matchforge.persistence.WalWriter;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The single writer of the whole system. Runs on exactly one dedicated
 * thread. Any number of gateway/client threads may call enqueue()
 * concurrently, but only this one thread ever assigns sequence numbers,
 * appends to the WAL, and dispatches to per-symbol matching cores. This
 * is what makes total ordering across symbols, durability-before-dispatch,
 * and deterministic replay all possible without any locking inside the
 * matching hot path itself.
 */
public final class Sequencer implements Runnable {

    private final BlockingQueue<Command> inbound = new LinkedBlockingQueue<>();
    private final Map<String, MatchingCore> coresBySymbol = new ConcurrentHashMap<>();
    private final WalWriter wal;
    private final AtomicLong seqCounter;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    public Sequencer(WalWriter wal, long startingSequence) {
        this.wal = wal;
        this.seqCounter = new AtomicLong(startingSequence);
        this.thread = new Thread(this, "sequencer");
        this.thread.setDaemon(true);
    }

    public void registerCore(MatchingCore core) {
        coresBySymbol.put(core.getSymbol(), core);
    }

    public void start() { thread.start(); }

    public void shutdown() {
        running.set(false);
        thread.interrupt();
    }

    /** Called by any number of gateway/client threads. Never blocks on matching or persistence. */
    public void enqueue(Command command) {
        inbound.add(command);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Command cmd = inbound.take();
                long seq = seqCounter.incrementAndGet();
                cmd.setSequence(seq);
                wal.append(cmd);
                MatchingCore core = coresBySymbol.get(cmd.getSymbol());
                if (core != null) {
                    core.submit(cmd);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public long currentSequence() { return seqCounter.get(); }
}
