# SyncMesh — Distributed Messaging Sandbox

SyncMesh is a teaching-focused distributed messaging system that blends Spring Boot, ZooKeeper coordination, Lamport clocks, and vector clocks to demonstrate how modern data platforms keep messages consistent across multiple nodes. It ships with a React-free (vanilla JS) operations dashboard, an opt-in CLI client, and a set of admin endpoints you can use to simulate partitions, observe leader election, and replay replication logs.

> Built as part of the SLIIT Distributed Systems coursework by  
> IT23656338 (Thewmika W A P), IT23651210 (Thilakarathna J K D R S), IT23689176 (Tharuka H M N D), IT23592506 (Karunarathna H W H H), IT23680470 (G D C Dabarera)

---

## Table of Contents
1. [Key Features](#key-features)
2. [Repository Layout](#repository-layout)
3. [System Architecture](#system-architecture)
4. [Prerequisites](#prerequisites)
5. [Quick Start](#quick-start)
6. [Running Multiple Nodes](#running-multiple-nodes)
7. [Operational Dashboard](#operational-dashboard)
8. [CLI Smoke Test](#cli-smoke-test)
9. [Admin & Diagnostics APIs](#admin--diagnostics-apis)
10. [Testing](#testing)
11. [Roadmap Ideas](#roadmap-ideas)

---

## Key Features
- **ZooKeeper-backed coordination** – Each server registers as an ephemeral znode under `/dms-system/servers`, emits heartbeat znodes, and participates in a simple leader election using sequential znodes.
- **Hybrid clocks** – Messages capture both Lamport logical clocks and a per-node vector clock map so replicas can reason about causality when conflicts arise.
- **Dynamic replication** – `ReplicationService` keeps an in-memory list of peer HTTP endpoints, pushes unicast or broadcast copies, and replays a short log to newcomers for quick catch-up.
- **Partition simulation** – Toggle partition mode via REST to see how the cluster behaves when quorum cannot be met (warnings instead of hard failures).
- **Operations dashboard** – A lightweight static UI (no build step) shows membership, heartbeats, leader state, replica targets, and message flow with SENT/RECEIVED highlighting.
- **CLI discovery client** – A Spring Boot CommandLineRunner samples ZooKeeper, discovers a live server, and fires a test message as a sanity check.
- **Polyglot-friendly contract** – Shared `common` module exposes `Message`, `NodeInfo`, and `Config` so new clients/services can align on payload format without duplicating code.

---

## Repository Layout
```
SyncMesh/
├── common/   # Shared domain objects and config constants
├── server/   # Spring Boot node that stores, replicates, and visualizes messages
├── client/   # CLI/automation client that discovers a node via ZooKeeper
├── tests/    # Placeholder module to host integration tests
├── pom.xml   # Maven parent + dependency management
└── README.md
```

---

## System Architecture
**Control plane**  
`ZooKeeperConnector` bootstraps every node by:
- Ensuring the `/dms-system` root, `/servers`, `/heartbeats`, and `/election` znodes exist.
- Publishing its own `NodeInfo` under `/servers/<nodeId>` as an ephemeral node (so crashed nodes automatically disappear).
- Updating a heartbeat znode every 2s (consumed by `/admin/heartbeats` and the UI).
- Participating in leader election via `/election/node-XXXX` sequential znodes. The node owning the smallest sequence becomes leader.

**Data plane**  
```
REST request → MessageController → MessageService
   ↳ validates receiver membership via ZooKeeper
   ↳ bumps Lamport clock + vector clock
   ↳ persists message in an in-memory repository with last-writer-wins semantics
   ↳ calls ReplicationService.replicate(...) or .broadcast(...)
```
Replication uses simple HTTP fan-out with quorum tracking for unicast writes and best-effort fan-out for broadcasts. Replication targets are refreshed via ZooKeeper watches plus a 10s polling safety net.

**Observability**  
The dashboard (`server/src/main/resources/static/`) consumes `/admin/**` endpoints to visualize cluster health, let you trigger elections, toggle partition mode, and inspect message flow from the perspective of the node hosting the UI.

---

## Prerequisites
- Java 17+
- Maven 3.9+
- Apache ZooKeeper 3.9.x (standalone instance is enough; default connect string `localhost:2181`)
- Modern browser for the dashboard (no bundler/build step required)

---

## Quick Start
1. **Run ZooKeeper**
   ```powershell
   # Windows example (adjust path to your ZooKeeper install)
   .\bin\zkServer.cmd
   ```
2. **Build every module**
   ```powershell
   .\mvnw.cmd -DskipTests install
   ```
3. **Start your first server node**
   ```powershell
   cd server
   java -jar target\server-0.0.1-SNAPSHOT.jar --server.port=8081 --node.id=server-8081
   ```
4. **Open the dashboard** – navigate to `http://localhost:8081/` and you should see node metadata, leader info, and the send-message form.

That's enough to observe a single-node deployment. Continue with the next section to simulate a cluster.

---

## Running Multiple Nodes
Run each node in its own terminal so logs remain readable:
```powershell
# Node 1
java -jar target\server-0.0.1-SNAPSHOT.jar --server.port=8081 --node.id=server-8081

# Node 2
java -jar target\server-0.0.1-SNAPSHOT.jar --server.port=8082 --node.id=server-8082

# Node 3 (optional)
java -jar target\server-0.0.1-SNAPSHOT.jar --server.port=8083 --node.id=server-8083
```
Every node automatically:
- Registers itself in ZooKeeper (so the dashboard immediately shows it).
- Rehydrates the replica list and replays recent messages to newcomers.
- Receives broadcasts (`receiver=BROADCAST`) and unicast messages where it is the target.

Use the dashboard on any node to send inter-node messages, broadcast announcements, or inspect health. Because the static assets are served locally, `http://localhost:PORT/` always shows the perspective of that node (helpful for testing replica filtering logic).

---

## Operational Dashboard
- **Cluster Nodes panel** – lists known servers, highlights the current node, and populates sender/receiver dropdowns.
- **Health & Topology** – exposes quick buttons to refresh heartbeats, replicas, leader info, and to manually trigger leader elections or partition mode.
- **Send Message** – builds JSON payloads for `/api/messages/send`. You can target a specific node or broadcast to all.
- **Messages panel** – filters history relative to the current node (SENT, RECEIVED, or both) and shows Lamport clock + origin node metadata for debugging races.

The UI lives in `server/src/main/resources/static` so feel free to customize copy, colors, or add charts without touching a React/Vite toolchain.

---

## CLI Smoke Test
The client module is a minimalist way to verify discovery + send logic without the browser.
```powershell
cd client
java -jar target\client-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none
```
The `CliRunner` will:
1. Connect to ZooKeeper using `Config.ZK_CONNECT`.
2. Grab the first available server from `/dms-system/servers`.
3. POST a sample message (`"hello from client"`) via `MessageSender`.

Tail server logs to confirm the inbound message, Lamport bump, and replication fan-out.

---

## Admin & Diagnostics APIs
| Endpoint | Purpose |
| --- | --- |
| `GET /admin/nodes` | List `NodeInfo` objects discovered via ZooKeeper. |
| `GET /admin/messages` | Dump in-memory message store for observability. |
| `GET /admin/heartbeats` | Inspect heartbeat timestamps per node. |
| `GET /admin/leader` | Show the node id recognized as leader. |
| `GET /admin/replicas` | Return the HTTP replica list maintained by `ReplicationService`. |
| `GET /admin/refresh-replicas` | Force-refresh replica list from ZooKeeper. |
| `GET /admin/partition/enable|disable` | Toggle partition mode (skips quorum enforcement). |
| `GET /admin/trigger-election` | Manually prompt a leader re-evaluation. |
| `GET /admin/test/unicast?target=<nodeId>` | Fire a diagnostic message directly at a node. |

All endpoints return JSON or plain text and are safe to call via the provided UI buttons or your own tooling (curl/Postman).

---

## Testing
`tests` currently contains a placeholder integration suite (`IntegrationTests.java`). Recommended next steps:
- Add multi-node integration tests using `curator-test` to spin up an in-memory ZooKeeper and drive multiple `ServerApplication` instances.
- Cover conflict resolution by asserting Lamport/vector precedence when two nodes race to update the same message id.

To execute the existing (placeholder) suite:
```powershell
mvn test -pl tests
```

---

## Roadmap Ideas
- Persist messages to an embedded DB (PostgreSQL, MongoDB, or RocksDB) so replicas survive restarts.
- Replace simple HTTP replication with gRPC streaming or Kafka-based change-log replication.
- Extend the CLI into a proper SDK with support for custom payload schemas and retries.
- Add Grafana/Loki exporters for heartbeats, replication latency, and partition-mode alerts.
- Strengthen tests with chaos scenarios (random node churn, forced ZooKeeper disconnects).

---

Happy hacking! If you build on top of SyncMesh, feel free to add your own module, mention the project in your portfolio, or extend the README with your learnings.

