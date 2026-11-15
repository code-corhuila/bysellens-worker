# bysellens-worker

> Asynchronous jobs and background processing

Part of the **LMS Library** distributed system — team `lms-library`, Grupo 2.
Governance and documentation live in [`library-docs`](https://github.com/code-corhuila/library-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `library-docs`.

## BY_SELLENS Worker Components

### Purpose

This repository includes selected existing BY_SELLENS message-processing and RabbitMQ
configuration source from the backend. These files are source components, not a standalone
worker application.

### Source Code

The Java files are copied from the BY_SELLENS backend with their original package declarations
and contents:

- `sales-service/src/main/java/com/bysellens/sales/SalesMessages.java`
- `sales-service/src/main/java/com/bysellens/sales/MessagingConfig.java`
- `inventory-service/src/main/java/com/bysellens/inventory/InventoryMessages.java`
- `inventory-service/src/main/java/com/bysellens/inventory/MessagingConfig.java`

### Included Components

- `SalesMessages` consumes sales-result events and updates a sale to confirmed or rejected after
  validating the event fields.
- `InventoryMessages` consumes inventory-confirmation events, checks the event against the
  reservation details, transitions a reserved inventory record to confirmed, and inserts the
  result event into the outbox.
- Each service's `MessagingConfig` declares the sales direct exchange, confirmation and result
  queues, dead-letter exchange and queues, and their bindings. Each file also contains an
  `OutboxPublisher` component.

### RabbitMQ Message Processing

The listeners use the `bysellens.inventory.confirm.v1` and `bysellens.sales.result.v1` queues.
Their listener methods are transactional and validate message version, identifiers, reservation
and sale states, and (for confirmation) reservation details before making updates. Queue
declarations include dead-letter routing for failed confirmation and result messages.

### Transactional Outbox

Each included `OutboxPublisher` is conditional on `outbox.enabled` (enabled when the property is
absent) and checks for unsent `rabbit_outbox` rows on a scheduled interval (2 seconds by default).
It locks and reads up to 20 rows, publishes persistent JSON messages, and marks a row sent only
after a broker acknowledgement with no returned message. A publish failure is logged and stops
the current batch; the row remains unsent.

### Dependencies

The source imports Spring AMQP, Spring scheduling/context, Spring JDBC and transaction APIs,
Jackson, SLF4J, and Java standard-library classes. It also relies on the original service
applications' repositories, domain classes, database schema, and RabbitMQ configuration; those
are not included here.

### Execution Status

This repository contains source components only. No standalone application execution, deployment,
or tests are claimed by this README.

### Important Notes

The Java logic and package declarations are preserved from the BY_SELLENS backend. These files
are not independent microservices and should not be launched as duplicate consumers alongside
the original application. The existing LMS Library and branching information above is retained.
