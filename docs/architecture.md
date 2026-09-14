# MatchForge Architecture

## Overview

MatchForge is a price-time-priority limit order matching engine. The
design goal throughout: no locks on the hot matching path, deterministic
recoverable state, and every component small enough to defend line-by-line
in an interview.

## Data flow

```
Client / NIO TCP Gateway
        │
        ▼
   Gateway.submitNewOrder() ──▶ RiskFilter.check() (throws RiskRejectionException on failure)
        │
        ▼
   Sequencer.enqueue(Command)          <- any number of producer threads may call this
        │  (single sequencer thread consumes this queue)
        ▼
   seq = seqCounter.incrementAndGet()
   WalWriter.append(cmd)                <- durable BEFORE dispatch
        │
        ▼
   MatchingCore.submit(cmd)             <- routed to the core owning cmd.getSymbol()
        │  (single matching thread per symbol consumes this queue)
        ▼
   MatchingCore.apply(cmd)
        │
        ├─▶ MatchingStrategy.match(book, order, seq)   <- mutates OrderBook, returns List<Trade>
        │
        ├─▶ MarketDataPublisher.publishTrade / publishBookUpdate   <- async, off hot path
        │
        └─▶ MetricsRegistry.recordMatchLatency / incrementOrders / incrementTrades
```

## Module -> package mapping

| Module (from the proposal) | Package                          | Key classes |
|---|---|---|
| Model / domain              | `com.matchforge.model`           | `Order`, `Trade`, `Side`, `OrderStatus` |
| Command pattern              | `com.matchforge.command`        | `Command`, `NewOrderCommand`, `CancelCommand`, `ModifyCommand` |
| Order book                   | `com.matchforge.book`           | `OrderBook` (TreeMap + Deque + HashMap index) |
| Matching algorithm            | `com.matchforge.matching`      | `MatchingStrategy` (interface), `PriceTimePriorityStrategy` |
| Single-writer sequencer       | `com.matchforge.sequencer`     | `Sequencer` |
| Per-symbol matching thread    | `com.matchforge.engine`        | `MatchingCore` |
| Persistence / WAL / snapshot  | `com.matchforge.persistence`   | `WalWriter`, `WalReader`, `CommandCodec`, `SnapshotManager` |
| Crash recovery                | `com.matchforge.recovery`      | `RecoveryManager` |
| Market data pub-sub           | `com.matchforge.marketdata`    | `MarketDataPublisher`, `MarketDataListener` |
| Pre-trade risk checks         | `com.matchforge.risk`          | `RiskFilter` |
| Client-facing API             | `com.matchforge.gateway`       | `Gateway` (in-process), `NioTcpGateway` (NIO/Selector TCP) |
| Metrics / observability       | `com.matchforge.metrics`       | `MetricsRegistry`, `MetricsHttpServer` |
| Exceptions                    | `com.matchforge.exceptions`    | `RiskRejectionException`, `InvalidOrderException`, `RecoveryException`, `DuplicateOrderException` |
| Test harness (no JUnit dep)   | `com.matchforge.testharness`   | `Test` annotation, `TestRunner` (reflection-based), `Assertions` |
| Tests                         | `com.matchforge.tests`         | `OrderBookTest`, `MatchingLogicTest`, `WalRecoveryTest`, `SequencerConcurrencyStressTest`, `ReplayDeterminismTest` |
| Benchmark                     | `com.matchforge.benchmark`     | `SimpleBenchmark` |

## Why the sequencer is a dedicated thread, not a `synchronized` method

An earlier design had every gateway thread call a `synchronized` method
to grab a sequence number, append to the WAL, and dispatch - correct, but
it makes every producer thread block on I/O (the WAL append) while
holding the lock, which is exactly the kind of contention this project
exists to avoid discussing badly in an interview.

The shipped design instead has a **single dedicated sequencer thread**
that owns a `BlockingQueue<Command>` as its only inbound synchronization
point. Any number of gateway/client threads call `enqueue()` and return
immediately - they never touch the WAL or the sequence counter directly.
Only the sequencer thread assigns sequence numbers, writes the WAL, and
dispatches to the right `MatchingCore`. This is the textbook single-writer
principle (as used by systems like the LMAX Disruptor), implemented here
with a plain JDK `BlockingQueue` rather than a hand-rolled ring buffer -
correct and simple first; a lock-free ring buffer is a documented future
optimization once benchmarking shows the queue itself is the bottleneck
(see `docs/benchmark-results.md`).

## Concurrency model summary

- **Gateway threads**: any number, never block on matching or I/O.
- **Sequencer thread**: exactly one. Owns sequence assignment + WAL
  writes + dispatch. This is the only true serialization point in the
  system and the first place to look when diagnosing a throughput ceiling.
- **MatchingCore threads**: one per symbol. Each owns exactly one
  `OrderBook` with zero locking inside it, because nothing else ever
  touches that book. Adding a new symbol adds a new independent thread -
  this is how the system scales across cores.
- **MarketDataPublisher thread**: exactly one, draining an internal queue
  and fanning out to listeners - decoupled so a slow listener can never
  stall matching.

## Persistence format

Each WAL record on disk:

```
[4 bytes payload length][8 bytes sequence][payload bytes][8 bytes CRC32 checksum]
```

The checksum covers `sequence + payload`, so a torn write (crash mid-append)
is detected by the reader and the trailing record is discarded rather than
trusted - this is exercised directly by `WalRecoveryTest#tornTailRecordIsDiscardedNotTrusted`,
which truncates a real WAL file mid-record and asserts recovery still
produces the correct (smaller) result.

Snapshots (`SnapshotManager`) dump every resting order plus the sequence
number they reflect, so `RecoveryManager` only needs to replay WAL records
*after* that sequence - bounding recovery time independent of how long the
engine has been running.

## Known simplifications (intentional, documented rather than hidden)

- Single matching strategy shipped (`PriceTimePriorityStrategy`); the
  `MatchingStrategy` interface is the seam for adding pro-rata later.
- `Sequencer`'s inbound queue is an unbounded `LinkedBlockingQueue`, not a
  bounded ring buffer - correct and simple; bounding it (with an explicit
  backpressure/reject policy) is the natural next step once you need
  admission control under overload.
- The NIO TCP gateway (`NioTcpGateway`) uses a minimal line protocol for
  demonstration, not a real FIX subset - by design, per the "do not build
  initially" list in the original proposal.
- No authentication on the NIO gateway in this MVP/advanced build; the
  `RiskFilter` and `Gateway` layers are where an auth check would be added.
