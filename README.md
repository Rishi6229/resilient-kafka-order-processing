# 🚀 Resilient Kafka Order Processing System

A **fault-tolerant, event-driven order processing system** built with Apache Kafka, designed to handle real-world failure scenarios using multi-stage retries, dead-letter queues (DLQ), and idempotent consumers. This project demonstrates how to build reliable distributed systems that process events safely under failures without data loss or duplication.

## 📌 Quick Links
- [Overview](#overview)
- [Architecture](#architecture)
- [Key Concepts](#key-concepts)
- [How It Works](#how-it-works)
- [Design Decisions](#design-decisions)
- [Getting Started](#getting-started)

---

## 📋 Overview

This system ensures reliable order processing in a distributed environment by:
- ✅ **Preventing Data Loss** - Using retry topics and DLQ
- ✅ **Avoiding Duplicates** - Implementing idempotent consumers
- ✅ **Handling Failures** - Multi-stage retry strategy with timeout protection
- ✅ **Maintaining Order** - Partition-based ordering per user (userId as key)

### Perfect For:
- Real-time order processing systems
- Event-driven microservices
- Building resilient distributed systems
- Understanding Kafka best practices

---

## 🧠 Key Concepts Implemented

| Concept | Description |
|---------|-------------|
| **Event-Driven Architecture** | Asynchronous, decoupled order processing |
| **Kafka Partitioning** | Uses `userId` as key for per-user ordering guarantee |
| **Manual Offset Management** | Explicit control over offset commits |
| **Multi-Level Retry Strategy** | 2 retry stages before moving to DLQ |
| **Dead Letter Queue (DLQ)** | Isolates failed messages for investigation |
| **Idempotent Consumers** | Duplicate-safe processing using message deduplication |
| **Failure Isolation & Recovery** | Separate consumers for each retry stage |

---

## 🏗️ Architecture

### System Flow Diagram

```
                ┌──────────────┐
                │   REST API   │
                │ /orders POST │
                └──────┬───────┘
                       │
                       ▼
                ┌──────────────────┐
                │    Producer      │
                │   (sendOrder)    │
                └──────┬───────────┘
                       │
                       ▼
                ┌──────────────────┐
                │  orders (Topic)  │
                │  [Partitioned]   │
                └──────┬───────────┘
                       │
                       ▼
            ┌──────────────────────────┐
            │   Main Consumer          │
            │  processOrder()          │
            └──────┬───────────────────┘
                   │
         ┌─────────┴──────────┐
         │                    │
         ▼                    ▼
   ✅ SUCCESS            ❌ FAILURE
   (offset commit)            │
                              ▼
                   ┌──────────────────────┐
                   │ orders-retry-1 Topic │
                   └──────┬───────────────┘
                          ▼
                  ┌────────────────────┐
                  │ Retry Consumer 1   │
                  └──────┬─────────────┘
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
       ✅ SUCCESS              ❌ FAILURE
                                   │
                                   ▼
                    ┌──────────────────────┐
                    │ orders-retry-2 Topic │
                    └──────┬───────────────┘
                           ▼
                   ┌────────────────────┐
                   │ Retry Consumer 2   │
                   └──────┬─────────────┘
                          │
               ┌──────────┴──────────┐
               ▼                     ▼
          ✅ SUCCESS         ❌ FINAL FAILURE
                                    │
                                    ▼
                        ┌──────────────────┐
                        │   orders-dlq     │
                        │  (Dead Letter Q) │
                        └──────────────────┘
                        (Requires manual
                         investigation)
```

### Retry Strategy Table

| Stage | Topic | Max Attempts | Purpose |
|-------|-------|------|---------|
| **1** | `orders` | 1 | Initial processing |
| **2** | `orders-retry-1` | 1 | First retry (transient failures) |
| **3** | `orders-retry-2` | 2 | Second retry (grace period) |
| **Final** | `orders-dlq` | - | Dead Letter Queue (permanent failures) |

---

## ⚙️ How It Works

### 1️⃣ **Order Creation**
```
API Request → Producer publishes event to Kafka (orders topic)
```
The REST API receives an order and publishes it to the main `orders` topic with `userId` as the partition key.

### 2️⃣ **Main Processing**
```
Main Consumer reads from orders topic → Processes order → Success?
```
- **If Success**: Offset is committed, message processing complete
- **If Failure**: Message sent to `orders-retry-1` topic

### 3️⃣ **Retry Logic**
```
Retry Consumer 1 → orders-retry-1 topic → Success?
  ├─ YES: Commit offset (done)
  └─ NO: Send to orders-retry-2

Retry Consumer 2 → orders-retry-2 topic → Success?
  ├─ YES: Commit offset (done)
  └─ NO: Send to orders-dlq (dead letter)
```

### 4️⃣ **Dead Letter Queue (DLQ)**
```
Failed messages → orders-dlq topic → Manual investigation
```
Messages that fail all retries are moved to DLQ for:
- Root cause analysis
- Manual reprocessing
- Debugging and monitoring

---

## 🧠 Idempotency Handling

**Problem**: Kafka guarantees **at-least-once delivery**, meaning a message might be processed multiple times due to retries or failures.

**Solution**: Implement idempotent processing using:
- **Unique Message ID**: `orderId` as unique identifier
- **Deduplication Store**: In-memory `ConcurrentHashMap` tracking processed `orderId`s
- **Result**: Same message processed multiple times → No duplicate effect ✅

```java
// Pseudocode
if (idempotencyStore.contains(orderId)) {
    return "Already processed"; // Skip duplicate
}
processOrder(order);
idempotencyStore.add(orderId);
```

---

## 🔥 Key Design Decisions

### ✅ **Why Retry Topics Instead of Consumer Retries?**
| Aspect | Consumer Retry | Retry Topics |
|--------|---|---|
| **Blocking** | ❌ Blocks main thread | ✅ Non-blocking |
| **Isolation** | ❌ Fails entire consumer | ✅ Isolated per stage |
| **Control** | ❌ Hard to manage | ✅ Fine-grained control |
| **Scaling** | ❌ Single consumer | ✅ Separate consumers per stage |

### ✅ **Why Dead Letter Queue?**
- ✅ **Prevents Infinite Loops**: Stops retry storms after max attempts
- ✅ **Isolates Poison Messages**: Bad messages don't block healthy ones
- ✅ **Enables Debugging**: All failed messages in one place
- ✅ **System Stability**: Prevents system overload

### ✅ **Why Same Partition Count Across Topics?**
- ✅ **Consistent Key Hashing**: Same `userId` always hashes to same partition
- ✅ **Preserves Order**: Per-user ordering maintained across retry topics
- ✅ **Simplified Reprocessing**: Messages flow through system in predictable paths

### ❌ **Why NOT Thread.sleep() for Retries?**
```
Thread.sleep() → Blocks consumer thread
              → Increases lag
              → Causes head-of-line blocking
              → Wastes resources
```
**Better**: Use separate retry topics with delayed processing

---

## ⚠️ Limitations & Trade-offs

| Limitation | Current | Recommendation |
|-----------|---------|---|
| **Idempotency Store** | In-memory (lost on restart) | Use Redis/DB for persistence |
| **Retry Delay** | Immediate | Implement exponential backoff |
| **Monitoring** | None | Add Prometheus + Grafana |
| **Schema Validation** | Basic JSON | Use Schema Registry (Avro/Protobuf) |
| **Exactly-Once Semantics** | Not implemented | Enable Kafka transactions |

---

## 🚀 Future Improvements

- [ ] **Redis-based Idempotency Store** - Persist processed orders across restarts
- [ ] **Exponential Backoff Retry Mechanism** - Intelligent retry delays
- [ ] **Kafka Lag Monitoring** - Prometheus + Grafana dashboards
- [ ] **Schema Registry** - Avro/Protobuf schema validation
- [ ] **Exactly-Once Semantics** - Enable transactional processing
- [ ] **Observability** - Distributed tracing with Jaeger/Zipkin
- [ ] **Circuit Breaker Pattern** - Fail fast for downstream service failures

---

## 🛠️ Tech Stack

```
Backend:
  ✓ Java 17
  ✓ Spring Boot 3.x
  ✓ Apache Kafka 3.x
  
Infrastructure:
  ✓ Docker & Docker Compose
  ✓ ZooKeeper (Kafka coordination)
  
Utilities:
  ✓ Jackson (JSON serialization)
  ✓ SLF4J (Logging)
  ✓ Maven (Build tool)
```

---

## ▶️ Getting Started

### Prerequisites
- Docker & Docker Compose installed
- Java 17+
- Maven 3.8+

### Setup Instructions

#### 1. **Clone the Repository**
```bash
git clone https://github.com/Rishi6229/resilient-kafka-order-processing.git
cd resilient-kafka-order-processing
```

#### 2. **Start Kafka & ZooKeeper**
```bash
docker compose up -d
```
This starts:
- Kafka broker on `localhost:9092`
- ZooKeeper on `localhost:2181`
- Creates topics: `orders`, `orders-retry-1`, `orders-retry-2`, `orders-dlq`

#### 3. **Verify Kafka is Ready**
```bash
docker exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

#### 4. **Run Spring Boot Application**
```bash
mvn clean install
mvn spring-boot:run
```
Application starts on `http://localhost:8080`

#### 5. **Monitor Kafka Topics** (Optional)
```bash
# List all topics
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor orders topic
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning
```

---

## 💡 API Usage

### Create an Order
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ord-001",
    "userId": "user-1",
    "amount": 150.00
  }'
```

### Request Schema
```json
{
  "orderId": "string",      // Unique order identifier
  "userId": "string",       // User identifier (partition key)
  "amount": number          // Order amount
}
```

### Response (Success)
```json
{
  "status": "Order received and processing started",
  "orderId": "ord-001"
}
```

### Response (Failure)
```json
{
  "status": "Error processing order",
  "message": "Invalid order details"
}
```

---

## 📊 Example Scenarios

### Scenario 1: Successful Processing
```
Order: ord-001 → orders topic
              → Main Consumer processes ✅
              → Offset committed
              → [END]
```

### Scenario 2: Transient Failure (Network Timeout)
```
Order: ord-002 → orders topic
              → Main Consumer fails ❌
              → orders-retry-1 topic
              → Retry Consumer 1 processes ✅
              → Offset committed
              → [END]
```

### Scenario 3: Poison Message (Bad Data)
```
Order: ord-003 → orders topic
              → Main Consumer fails ❌ (Bad data)
              → orders-retry-1 topic
              → Retry Consumer 1 fails ❌
              → orders-retry-2 topic
              → Retry Consumer 2 fails ❌
              → orders-dlq topic
              → [REQUIRES MANUAL INTERVENTION]
```

---

## 🧪 Testing

### Manual Testing
```bash
# Terminal 1: Monitor DLQ
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic orders-dlq \
  --from-beginning

# Terminal 2: Send problematic order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "bad", "userId": "", "amount": -100}'
```

---

## 📚 Learning Resources

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Boot + Kafka Integration](https://spring.io/projects/spring-kafka)
- [Idempotent Consumer Pattern](https://kafka.apache.org/documentation/#semantics)
- [Dead Letter Queue Pattern](https://en.wikipedia.org/wiki/Dead_letter_queue)
- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)

---

## 🤝 Contributing

Contributions are welcome! Please feel free to:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## 📄 License

This project is open source and available under the MIT License.

---

## 💬 Questions or Issues?

Feel free to open an issue on GitHub or reach out with questions. Happy building! 🎉

---

**Last Updated**: April 2026  
**Maintained by**: [@Rishi6229](https://github.com/Rishi6229)
