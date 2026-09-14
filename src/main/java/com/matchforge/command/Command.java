package com.matchforge.command;

/**
 * Command pattern base class: every mutation to the system (new order,
 * cancel, modify) is represented as a Command so it can be uniformly
 * sequenced, persisted to the WAL, and replayed for recovery/testing.
 */
public abstract class Command {
    private long sequence = -1;
    private final long submittedAtNanos = System.nanoTime();

    public long getSequence() { return sequence; }
    public void setSequence(long seq) { this.sequence = seq; }
    public long getSubmittedAtNanos() { return submittedAtNanos; }

    public abstract String getSymbol();
    public abstract CommandType getType();
}
