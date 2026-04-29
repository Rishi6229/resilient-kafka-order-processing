🚀 Resilient Kafka Order Processing System
📌 Overview

A fault-tolerant, event-driven order processing system built using Apache Kafka, designed to handle real-world failure scenarios using multi-stage retries, dead-letter queues (DLQ), and idempotent consumers.

This project demonstrates how to build reliable distributed systems that can process events safely under failures without data loss or duplication.

🧠 Key Concepts Implemented
Event-Driven Architecture
Asynchronous Processing using Kafka
Partition-based Ordering (using userId as key)
Manual Offset Management
Multi-Level Retry Strategy
Dead Letter Queue (DLQ)
Idempotent Consumers (Duplicate-safe processing)
Failure Isolation & Recovery
🏗️ Architecture
                ┌──────────────┐
                │   REST API   │
                └──────┬───────┘
                       │
                       ▼
                ┌──────────────┐
                │   Producer   │
                │  (sendOrder) │
                └──────┬───────┘
                       │
                       ▼
                ┌──────────────┐
                │    orders    │
                │   (Topic)    │
                └──────┬───────┘
                       │
                       ▼
            ┌──────────────────────┐
            │   Main Consumer      │
            │   processOrder()     │
            └──────┬───────────────┘
                   │
         ┌─────────┴─────────┐
         │                   │
         ▼                   ▼
   ✅ Success          ❌ Failure
   (ack commit)        │
                       ▼
              ┌──────────────────┐
              │ orders-retry-1   │
              └──────┬───────────┘
                     ▼
              Retry Consumer 1
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
   ✅ Success              ❌ Failure
                            │
                            ▼
                   ┌──────────────────┐
                   │ orders-retry-2   │
                   └──────┬───────────┘
                          ▼
                   Retry Consumer 2
                          │
                ┌─────────┴─────────┐
                ▼                   ▼
          ✅ Success         ❌ Final Failure
                                │
                                ▼
                        ┌──────────────┐
                        │  orders-dlq  │
                        └──────────────┘
🔁 Retry Strategy
Stage	Topic	Purpose
1	orders	Initial processing
2	orders-retry-1	First retry (transient failures)
3	orders-retry-2	Second retry
Final	orders-dlq	Permanent failures
⚙️ How It Works
1. Order Creation
API receives order request
Producer publishes event to Kafka (orders topic)
2. Processing
Consumer processes order asynchronously
If successful → offset committed
3. Failure Handling
On failure → message routed to retry topic
Retries happen in separate consumers
4. DLQ Handling
After max retries → message moved to DLQ
Prevents infinite retry loops and system blockage
🧠 Idempotency Handling

To handle Kafka’s at-least-once delivery, the system ensures:

Same message processed multiple times → No duplicate effect

Implemented using:

orderId as unique identifier
In-memory store (ConcurrentHashMap) to track processed orders
🔥 Key Design Decisions
✅ Why Retry Topics?
Avoid blocking main consumer
Isolate failure handling
Enable controlled retries
✅ Why DLQ?
Prevent infinite retry loops
Isolate poison messages
Enable debugging & reprocessing
✅ Why Same Partition Count?
Ensures consistent key hashing
Preserves per-user ordering across topics
✅ Why Not Thread.sleep()?
Blocks consumer thread
Increases lag
Causes head-of-line blocking
⚠️ Limitations (Honest Engineering)
Idempotency is in-memory (not persistent)
No retry delay (can be improved using backoff strategy)
No monitoring/metrics integration
🚀 Future Improvements
Redis-based idempotency store
Exponential backoff retry mechanism
Kafka lag monitoring (Prometheus + Grafana)
Schema Registry (Avro/Protobuf)
Exactly-once semantics (Kafka transactions)
🛠️ Tech Stack
Java 17
Spring Boot
Apache Kafka
Docker
Jackson (JSON serialization)
▶️ Running the Project
# Start Kafka
docker compose up -d

# Run Spring Boot app
mvn spring-boot:run
💡 Sample API Request
POST /orders
{
  "orderId": "123",
  "userId": "user-1",
  "amount": 100
}
