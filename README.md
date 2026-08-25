# Transactional Outbox with PostgreSQL and NATS

This project demonstrates the Transactional Outbox pattern in a distributed
Spring Boot application. Business data and outbound messages are written to
PostgreSQL in the same local transaction. A single active dispatcher reads
committed outbox rows in sequence and publishes them to NATS JetStream.

The dispatcher waits for a JetStream acknowledgement before marking a message
as published. If the process fails between those two operations, the message is
retried. Consumers must therefore be idempotent because duplicate delivery is
possible, as expected with at-least-once delivery.

The implementation deliberately uses polling rather than PostgreSQL
`LISTEN`/`NOTIFY`, so it does not reserve a database connection while waiting
for work.

## Running the example

Docker is the only external runtime requirement. To build the application,
start PostgreSQL, NATS, and three application replicas, and execute a payment
flow through a follower replica, run:

```shell
make demo
```

The command waits for every container to become healthy before sending HTTP
requests. `app1` is the mocked leader; `app2` and `app3` are followers. Use
`make down` to stop the example and remove its demo volumes.

## Testing

Run the complete suite with:

```shell
make test
```

The integration tests use Testcontainers to create isolated PostgreSQL and NATS
instances. They cover successful commit and publication, rollback without
publication, and FIFO publication. The Make target derives `DOCKER_HOST` from
the active Docker CLI context, which also supports Colima-based environments.

## Design assumptions

### FIFO ordering

FIFO currently means a global order across committed outbox messages. A
transactional counter allocates each message a sequence number, and the active
dispatcher publishes pending rows one at a time in that order.

This guarantee assumes that there is one effective dispatcher leader. The
provided leadership implementation is intentionally static because leader
election may be mocked for this example. The in-process dispatch lock only
coordinates work inside one application instance; it does not protect against
split leadership or configuration that designates multiple replicas as leader.
A production leader-election implementation would require a lease or fencing
mechanism.

The global sequence counter also serializes transactions that stage messages.
This is a conscious tradeoff for an unambiguous global commit order. For a
higher-throughput system, FIFO would normally be scoped to an aggregate or
partition key. Independent keys could then be dispatched concurrently while
messages belonging to the same key remain ordered.

### At-least-once delivery

Publishing and marking a row as published are not atomic, so crashes or uncertain acknowledgements can cause duplicate sends.

The outbox UUID is used as the NATS message ID so JetStream can suppress duplicates within its duplicate window. This is not an exactly-once guarantee; consumers should still process messages idempotently.

## Possible improvements

### Cross-replica wakeup through NATS

The post-commit wakeup is local to the replica handling the transaction, so a non-leader replica relies on the leader’s next database poll.

To reduce latency, publish a lightweight best-effort wakeup to Core NATS after commit. The leader can immediately drain the outbox, while periodic database polling remains the durable fallback. Because the signal is sent via `afterCommit`, rolled-back transactions cannot trigger it.

### Extract the implementation as a library

The reusable components are separated under the `library` package, but this
repository currently builds them together with the payment example. They could
be extracted into a dedicated Maven module.
