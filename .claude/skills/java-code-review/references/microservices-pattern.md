# Microservice & REST API Patterns

## Table of Contents
1. [REST API Design](#1-rest-api-design)
2. [Inter-Service Communication](#2-inter-service-communication)
3. [Resilience Patterns](#3-resilience-patterns)
4. [Data Consistency](#4-data-consistency)
5. [Common Microservice Anti-Patterns](#5-common-microservice-anti-patterns)

---

## 1. REST API Design

### Resource Naming
```
# BAD — verb-based
GET  /getUser?id=1
POST /createOrder
POST /deleteProduct/5

# GOOD — noun-based, hierarchical
GET    /users/1
POST   /orders
DELETE /products/5
GET    /users/1/orders       ← nested resource
```

### HTTP Status Codes (use correctly)
| Code | Meaning | Use when |
|------|---------|----------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | POST that creates a resource |
| 204 | No Content | DELETE or PUT with no body returned |
| 400 | Bad Request | Validation failure, malformed input |
| 401 | Unauthorized | Not authenticated |
| 403 | Forbidden | Authenticated but not authorized |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate, optimistic lock failure |
| 422 | Unprocessable Entity | Semantically invalid (domain rule violation) |
| 500 | Internal Server Error | Unexpected failure — log and return generic message |

### API Versioning
```
# URI versioning (most common, easiest to use)
/api/v1/orders
/api/v2/orders

# Header versioning (cleaner URLs, harder to test in browser)
Accept: application/vnd.myapp.v2+json
```
- Version from day 1 — retrofitting versioning is painful
- Never break existing clients on a published version
- Deprecate old versions with `Deprecation` and `Sunset` response headers

### Pagination & Filtering
```java
// Always paginate list endpoints
@GetMapping("/orders")
public Page<OrderDto> list(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(required = false) String status
) {
    return orderService.findAll(status, PageRequest.of(page, size));
}
```
- Never return unbounded lists — even "small" tables grow
- Consistent response envelope: `{ data: [...], page: 0, size: 20, total: 145 }`

### Request/Response DTOs
```java
// Separate DTOs per use case — don't reuse the same object for create/update/response
record CreateOrderRequest(@NotNull Long customerId, @NotEmpty List<OrderItem> items) {}
record UpdateOrderRequest(@NotBlank String status) {}
record OrderResponse(Long id, String status, LocalDateTime createdAt, List<OrderItemDto> items) {}
```

---

## 2. Inter-Service Communication

### Sync (HTTP/REST) — when to use
- Real-time response needed by the caller
- Simple request/response, low latency tolerance
- Use `RestTemplate` (legacy) or `WebClient` (reactive, preferred in new code)

```java
// GOOD — WebClient with timeout
@Bean
public WebClient paymentClient(WebClient.Builder builder) {
    return builder
        .baseUrl(paymentServiceUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
}

// Always set timeouts — no timeout = potential thread starvation
HttpClient httpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(5));
```

### Async (Messaging) — when to use
- Fire-and-forget or eventual consistency is acceptable
- Fan-out to multiple consumers
- Decoupling services for independent scaling
- Use Kafka, RabbitMQ, or Spring Cloud Stream

```java
// Producer
kafkaTemplate.send("order.created", new OrderCreatedEvent(order.getId(), order.getCustomerId()));

// Consumer
@KafkaListener(topics = "order.created", groupId = "notification-service")
public void onOrderCreated(OrderCreatedEvent event) {
    notificationService.sendConfirmation(event.getCustomerId());
}
```

### Service Discovery
- Use Spring Cloud's `@LoadBalanced` `RestTemplate`/`WebClient` with Eureka or Kubernetes DNS
- Never hardcode service hostnames — use logical names (`http://order-service/api/orders`)

---

## 3. Resilience Patterns

### Circuit Breaker (Resilience4j)
```java
@CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
public PaymentResult processPayment(PaymentRequest req) {
    return paymentClient.post()...;
}

public PaymentResult paymentFallback(PaymentRequest req, Exception ex) {
    log.warn("Payment service unavailable, using fallback", ex);
    return PaymentResult.pending(); // queue for retry
}
```

### Retry
```java
@Retry(name = "externalApi", fallbackMethod = "retryFallback")
public Data fetchFromExternalApi(String id) { ... }
```
- Use exponential backoff with jitter — don't hammer a struggling service
- Retry only on transient errors (5xx, timeout) — not on 4xx (those won't fix themselves)

### Timeout
- Always set connect + read timeouts on all HTTP clients
- Default `RestTemplate` has no timeout — this is a common production bug

### Bulkhead
- Isolate thread pools for different downstream services
- Prevents one slow dependency from exhausting the shared thread pool

---

## 4. Data Consistency

### Each Service Owns Its Data
- No service reads another service's DB directly
- Share data via API calls or events — not shared tables

### Saga Pattern (for distributed transactions)
- Choreography: each service publishes events, others react
- Orchestration: a central saga orchestrator directs steps and handles compensation

### Idempotency
```java
// Consumers must handle duplicate messages (at-least-once delivery)
@KafkaListener(...)
public void onEvent(MyEvent event) {
    if (processedEventRepo.existsById(event.getId())) return; // idempotency check
    // process...
    processedEventRepo.save(new ProcessedEvent(event.getId()));
}
```

### Optimistic Locking
```java
@Entity
public class Order {
    @Version
    private Long version; // JPA throws OptimisticLockException on concurrent update
}
```

---

## 5. Common Microservice Anti-Patterns

### Distributed Monolith
**Problem**: Services deployed separately but tightly coupled — every change requires coordinating multiple deployments.
**Fix**: Services should be independently deployable. Loose coupling via events or stable APIs.

### Chatty Services
**Problem**: Service A makes 10 calls to service B to render one page.
**Fix**: Aggregate at the API layer (BFF pattern) or use batch endpoints.

### Shared Database
**Problem**: Two services share the same DB schema — changes to one break the other.
**Fix**: Each service has its own schema/database. Share data via API or events.

### Missing Correlation ID
**Problem**: A request spans 5 services; you can't trace it through logs.
**Fix**: Generate a `correlationId` at the API gateway, pass it in headers, log it in every service.

```java
// Spring filter to propagate correlation ID
MDC.put("correlationId", request.getHeader("X-Correlation-Id"));
```

### No Health Checks
**Problem**: Service is up but not functional — DB connection pool exhausted, downstream hung.
**Fix**: `/actuator/health` with meaningful sub-checks (DB, cache, downstream services).

```java
@Component
public class PaymentServiceHealthIndicator implements HealthIndicator {
    public Health health() {
        boolean up = paymentClient.ping();
        return up ? Health.up().build() : Health.down().withDetail("reason", "payment-service unreachable").build();
    }
}
```