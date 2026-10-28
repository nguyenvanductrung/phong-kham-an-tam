# RabbitMQ topology

Compose runs RabbitMQ `4.2.9-management`; the topology is declared by `backend/common-lib/.../RabbitTopology.java` when `events.enabled=true`.

- Durable topic exchange: `clinic.events`; durable DLX: `clinic.events.dlx`.
- Durable queues: `notification.events`, `patient.events`, `scheduling.events`.
- Consumer failures retry five times with bounded exponential delay; final rejection is routed to `<consumer>.events.dlq`.
- Outbox publishing waits for publisher confirms, retries on later polls, and routes after eight failures to `outbox.events.dlq`.
- Consumers insert `processed_events` in the same DB transaction as their side effect. `eventId` is the idempotency key; scheduling projections also reject stale aggregate versions.
- Payloads contain identifiers and operational fields only. Clinical note content, diagnosis, prescription text, and file metadata must not be placed on the exchange.

The application declares queues and bindings at startup, so a clean Compose volume is initialized automatically. Inspect the queues at `http://localhost:15673` (or the configured `RABBITMQ_MANAGEMENT_HOST_PORT`). The host AMQP port defaults to `5673` (`RABBITMQ_HOST_PORT`); services inside Compose use `rabbitmq:5672`.
