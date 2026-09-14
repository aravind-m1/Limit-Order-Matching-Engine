# Limit-Order-Matching-Engine

Limit-Order-Matching-Engine is a deterministic limit order matching engine implemented with
plain Java 21. It uses price-time priority, single-writer sequencing, one
matching thread per symbol, write-ahead logging, snapshots, crash recovery,
asynchronous market data, pre-trade risk checks, an NIO TCP gateway, and a
JDK-only metrics endpoint.

The project is intentionally small and dependency-light so that the matching
path, persistence model, concurrency boundaries, and recovery behavior are
easy to inspect and run locally.

## Highlights

- Price-time priority matching with full and partial fills.
- Self-trade prevention and duplicate-order protection.
- Single-writer sequencing with gapless command ordering.
- Per-symbol order-book ownership with no locks on the matching hot path.
- Checksummed write-ahead log with torn-tail detection.
- Snapshot plus WAL-tail recovery.
- Asynchronous trade and book-update publication.
- NIO `Selector` TCP gateway for concurrent client connections.
- HTTP metrics endpoint using `com.sun.net.httpserver.HttpServer`.
- Custom JDK-only test harness and repeatable benchmark.

## Requirements

- JDK 21 or newer
- Bash for the convenience scripts on Linux, macOS, or Git Bash
- Maven is optional; the default build uses `javac` and has no runtime
  dependencies

## Build and run

From the repository root:

```bash
./build.sh
./run-tests.sh
./run-demo.sh
./run-benchmark.sh
./run-network-demo.sh
```

The test suite should finish with `10/10 tests passed`. The demo exercises
order submission, matching, metrics, snapshot creation, simulated shutdown,
and recovery from a snapshot plus the remaining WAL records.

### Windows PowerShell

The shell scripts require Bash. The equivalent build and test commands in
PowerShell are:

```powershell
$main = Get-ChildItem src\main\java -Recurse -Filter *.java | ForEach-Object FullName
$test = Get-ChildItem src\test\java -Recurse -Filter *.java | ForEach-Object FullName
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory out | Out-Null
javac -d out -encoding UTF-8 $main
javac -d out -cp out -encoding UTF-8 $test
java -cp out com.matchforge.RunTests
```

## TCP protocol

`NetworkDemo` starts the TCP gateway on port `9631` and metrics on port
`9632`. The gateway accepts one comma-separated command per line:

```text
NEW,<symbol>,<BUY|SELL>,<price>,<quantity>,<clientId>
CANCEL,<symbol>,<orderId>,<clientId>
```

Successful commands return an `ACK` line. Invalid commands and risk failures
return a `REJECT` line. For example:

```text
NEW,ACME,BUY,10000,25,client-a
```

The metrics endpoint is available at `http://localhost:9632/metrics` while
the network demo is running.

## Architecture

```text
Client / TCP gateway
        |
        v
RiskFilter -> Sequencer -> WAL -> MatchingCore per symbol -> OrderBook
                                      |
                                      +-> Trades and book updates
                                      +-> MetricsRegistry
```

The sequencer is the only component that assigns sequence numbers, appends
commands to the WAL, and dispatches them. Each `MatchingCore` exclusively
owns one order book, which makes matching deterministic without locks in the
hot path. Recovery loads the newest snapshot and replays only later WAL
records; each record is protected by a checksum so an incomplete final write
is ignored.

See [docs/architecture.md](docs/architecture.md) for the detailed data flow,
module map, persistence format, and design decisions.

## Tests and benchmarks

The tests cover order-book operations, full and partial fills, FIFO priority,
self-trade prevention, WAL round trips, torn WAL records, concurrent
sequencing, and deterministic replay. They run through the lightweight test
harness in `com.matchforge.testharness`.

The benchmark includes a warm-up phase, a measured phase, throughput, and
latency percentiles. Its captured results are environment-specific and are
provided as reference only in [docs/benchmark-results.md](docs/benchmark-results.md).

## Project layout

```text
src/main/java/com/matchforge/
  book/          Order book data structures
  command/       New, cancel, and modify commands
  engine/        Per-symbol matching core
  gateway/       In-process and NIO TCP gateways
  matching/      Matching strategy and price-time implementation
  marketdata/    Asynchronous market-data publisher
  metrics/       Counters, latency buckets, and HTTP endpoint
  model/         Orders, trades, sides, and statuses
  persistence/   WAL, codec, and snapshots
  recovery/      Snapshot and WAL recovery
  risk/          Pre-trade validation
  sequencer/     Single-writer command sequencing
src/test/java/   Behavior and concurrency tests
docs/             Architecture and benchmark documentation
build.sh          Plain javac build
run-*.sh          Demo, test, benchmark, and network commands
pom.xml           Optional Maven project metadata
```

## Scope and next steps

This is an educational and engineering-focused engine, not a production
exchange. The TCP protocol is deliberately minimal, authentication is not
included, the inbound queue is unbounded, and only price-time priority is
implemented. Natural next steps include bounded-queue backpressure, a
versioned client protocol, authentication, external load testing, and JMH
benchmarking.

## License

No license has been selected for this private repository. Add an explicit
license before distributing the project outside its intended audience.
