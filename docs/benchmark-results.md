# Benchmark Results

Captured with `run-benchmark.sh` (`SimpleBenchmark`) on the build/test container:
OpenJDK 21.0.12, single machine, no other load. These are illustrative
numbers from this environment, not a claim about production hardware -
re-run `run-benchmark.sh` on your own machine before quoting numbers in
an interview.

## Methodology

- 50,000 warm-up orders (JIT warm-up, discarded from measurement) followed by
  200,000 measured orders, matching the two-phase discipline JMH would use.
- Orders are synthetic: random side, price in a 10-tick range, quantity 1-20,
  spread across 50 distinct client IDs (so self-trade prevention does not
  suppress matching - see the note below).
- "Enqueue latency" measures wall-clock time for `Sequencer.enqueue()` to
  return from the calling (gateway) thread - i.e. how long a client-facing
  call takes to hand off, not full order-to-fill latency.
- "Match latency" (from `MetricsRegistry`) measures time inside
  `MatchingCore.apply()` on the matching thread itself - the true hot path.

## Results (most recent run)

```
Warming up JIT with 50000 orders...
Measuring throughput and latency over 200000 orders...

=== Benchmark Results ===
Enqueue throughput: 625,231 orders/sec
Enqueue latency p50:    186 ns
Enqueue latency p99:    274 ns
Enqueue latency p99.9:  722 ns
Total orders processed by matching core: 250,000

orders_processed 250000
trades_executed 193858
match_latency_bucket{le="lt_1us"}   223967
match_latency_bucket{le="lt_10us"}   24872
match_latency_bucket{le="lt_100us"}   1062
match_latency_bucket{le="lt_1ms"}        41
match_latency_bucket{le="lt_10ms"}       50
match_latency_bucket{le="gte_10ms"}       8
```

## Reading these numbers

- **~90% of match operations complete in under 1 microsecond** on the
  matching thread itself - expected, since the hot path is just TreeMap
  navigation and Deque operations with no I/O and no locking.
- **The long tail (the 8 samples at ≥10ms)** is consistent with JVM
  safepoint/GC pauses, not algorithmic cost - this is exactly why the
  proposal calls out object pooling/reuse of `Order`/`Trade` as the next
  optimization once tail latency matters more than it does at this scale.
- **Enqueue throughput (~625K/sec) is higher than match throughput** in
  this run because the `LinkedBlockingQueue` used for the sequencer's
  inbound queue lets producer threads return before the sequencer thread
  has drained the backlog - this is intentional backpressure absorption,
  not measurement error, and is why `waitForDrain()` polls until the
  matching core actually finishes.

## A real self-trade-prevention gotcha we hit while benchmarking

The first version of this benchmark used a single hardcoded `clientId`
for every synthetic order, which meant every incoming order was blocked
from matching **every** resting order by self-trade prevention -
`trades_executed` came back as exactly `0` across 250,000 orders. That's
the self-trade prevention logic working correctly, but it made for a
useless benchmark. Spreading orders across 50 client IDs fixed it. This
is a good story for an interview: it shows the difference between "the
code has a bug" and "the code is correct but your test harness doesn't
reflect the real world."

## What a production benchmark would add (not built here)

- **JMH** (`@Benchmark`, `@Warmup`, `@Measurement`, `@Fork`) instead of a
  hand-rolled loop, for JVM-level statistical rigor and fork isolation.
  Not used here only because this build environment has no Maven Central
  access to pull the JMH dependency - see `pom.xml`, which is ready to go
  once you have that access.
- Multi-threaded producer benchmarks (the correctness stress test already
  proves multiple producer threads work; a throughput benchmark under
  concurrent producers would be a natural next step).
- A load generator driving the NIO TCP gateway externally, to measure
  true end-to-end (network-in to ack-out) latency rather than in-process
  enqueue latency.
