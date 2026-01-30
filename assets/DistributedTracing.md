# Distributed Tracing with Micrometer + Zipkin: Complete Production Guide

> **For Senior Backend Architects & Observability Engineers**  
> From Zero to Production-Grade Mastery

---

## Quick Navigation

- [1. The Problem](#1-the-problem)
- [2. Core Concepts](#2-core-concepts)
- [3. Micrometer Architecture](#3-micrometer-architecture)
- [4. Zipkin Internals](#4-zipkin-internals)
- [5. Request Lifecycle](#5-request-lifecycle)
- [6. Configuration](#6-configuration)
- [7. Instrumentation](#7-instrumentation)
- [8. Logging Correlation](#8-logging-correlation)
- [9. Debugging](#9-debugging)
- [10. Production Best Practices](#10-production-best-practices)

---

## 1. The Problem

### Why Logs and Metrics Aren't Enough

**Monolith Reality:**
```
Request → Controller → Service → Repository → Database
[Single Stack Trace | Single Log File | One Process]
```

**Microservices Reality:**
```
Request → Gateway → Auth → Order → [Inventory, Payment, Shipping]
          [5 services × Different logs × Network failures × Retries]
```

### Real Failure Scenario

**Symptom:** "Checkout is sometimes slow" (P95 latency: 8 seconds)

**Logs show:**
```
2024-01-29 10:15:23 [order-service] Order created: ord_12345
2024-01-29 10:15:25 [payment-service] Payment processed
2024-01-29 10:15:31 [shipping-service] Shipment created
```

**Questions you CAN'T answer:**
1. Which service caused the 8-second delay?
2. Was it database? API call? Queue processing?
3. Did retries amplify the problem?
4. What was the actual call chain?

**With Distributed Tracing:**
```
Trace ID: abc123 [8.2s total]
├─ API Gateway [8.2s]
│  └─ Order Service [8.1s]
│     ├─ Inventory Check [0.1s] ✅
│     └─ Payment Call [8.0s] ❌ ROOT CAUSE
│        └─ DB Query SELECT * FROM accounts [7.9s] ← Missing index!
```

### The Mental Model

**Tracing = Debugger for Production**

| Local Debugger | Distributed Tracing |
|----------------|---------------------|
| Step through code | Step through services |
| Call stack | Trace tree |
| Local variables | Span tags/baggage |
| Breakpoints | Sampling |
| Single process | Multi-service |

---

## 2. Core Concepts

### 2.1 Trace

**Definition:** Complete journey of ONE request through your system

```
Trace abc123:
├─ Duration: 847ms
├─ Services: api-gateway, order, inventory, payment
├─ Spans: 23
└─ Status: ERROR (payment failed)
```

**Key Properties:**
- Globally unique ID (128-bit hex: `1a2b3c4d5e6f7890abcdef1234567890`)
- Survives across ALL service boundaries
- Can span minutes (for async workflows)

### 2.2 Span

**Definition:** Single unit of work with timing

```json
{
  "traceId": "abc123",
  "spanId": "span456",
  "parentSpanId": "span789",
  "name": "GET /api/orders",
  "kind": "SERVER",
  "timestamp": 1706544923000000,  // microseconds!
  "duration": 245000,  // 245ms
  "tags": {
    "http.method": "GET",
    "http.status_code": "200",
    "custom.user_id": "user123"
  }
}
```

**Span Kinds:**

| Kind | Description | Example |
|------|-------------|---------|
| SERVER | Receiving sync request | REST controller |
| CLIENT | Making sync call | HTTP request |
| PRODUCER | Sending async message | Kafka send |
| CONSUMER | Receiving async message | Kafka listener |
| INTERNAL | Internal operation | Business logic |

**Why Kind Matters:**
```
CLIENT span in Service A: 100ms
    ↓
SERVER span in Service B: 80ms
    
Network latency = 100ms - 80ms = 20ms
```

### 2.3 Parent-Child Relationships

```
Root Span (parentId: null)
├─ Child Span 1 (parentId: root)
│  ├─ Grandchild 1a (parentId: child1)
│  └─ Grandchild 1b (parentId: child1)
└─ Child Span 2 (parentId: root)
```

**Rules:**
1. Child's traceId = Parent's traceId (ALWAYS)
2. Child's parentId = Parent's spanId
3. Child duration ≤ Parent duration*

*Exception: Async operations

### 2.4 Context Propagation

**The Hard Problem:** How does Service B know it's part of Service A's trace?

**Solution: HTTP Headers (W3C Standard)**

```http
GET /api/orders HTTP/1.1
Host: order-service
traceparent: 00-abc123def456-span789-01
             ││ └traceId    └spanId  └sampled
             │└ version
             └ format

Baggage: userId=user123,tenantId=acme
```

**Spring Boot auto-injects this:**
```java
// You write:
restTemplate.getForObject("http://service-b/data", String.class);

// Spring automatically adds:
headers.set("traceparent", "00-{traceId}-{spanId}-01");
```

**Threading Challenge:**
```java
// Main thread
Span span = tracer.currentSpan();  // ✅ Works

new Thread(() -> {
    Span span2 = tracer.currentSpan();  // ❌ NULL! Context lost!
}).start();
```

**Solution:**
```java
Span parentSpan = tracer.currentSpan();

executor.execute(() -> {
    try (Tracer.SpanInScope ws = tracer.withSpan(parentSpan)) {
        // Context restored!
    }
});
```

### 2.5 Baggage vs Tags

| Feature | Tags | Baggage |
|---------|------|---------|
| **Scope** | Single span | Entire trace |
| **Propagation** | No | Yes (via headers) |
| **Searchable** | Yes (Zipkin indexes) | Only if copied to tag |
| **Performance** | Minimal | Costs (header bloat) |
| **Cardinality** | Can be high | MUST be low |

**Tags Example:**
```java
span.tag("http.method", "GET");           // ✅ Span-specific
span.tag("db.statement", "SELECT...");    // ✅ Local metadata
span.tag("http.status_code", "200");      // ✅ Indexed for search
```

**Baggage Example:**
```java
// Service A
tracer.createBaggage("userId", "user123");
tracer.createBaggage("tenantId", "acme");

// Service B (automatically has access!)
String userId = tracer.getBaggage("userId").get();  // "user123"

// Service C (still has access!)
String userId = tracer.getBaggage("userId").get();  // "user123"
```

**Headers sent:**
```http
Service A → B:
traceparent: 00-abc-123-01
baggage: userId=user123,tenantId=acme

Service B → C:
traceparent: 00-abc-456-01
baggage: userId=user123,tenantId=acme  ← Propagated!
```

**⚠️ Baggage Limits:**
```
Each entry: ~50-100 bytes
Total limit: < 1KB
Avoid: timestamps, UUIDs, session tokens
Use: userId, tenantId, featureFlags (low cardinality!)
```

---

## 3. Micrometer Tracing Architecture

### 3.1 The Stack

```
┌────────────────────────────────────┐
│ Your Application                    │
│ (Micrometer Tracing APIs)          │
└──────────────┬─────────────────────┘
               │
┌──────────────▼─────────────────────┐
│ Micrometer Tracing (Facade)        │
│ Like SLF4J for tracing             │
└──────────────┬─────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼─────┐   ┌─────▼──────┐
│ Brave      │   │ OTel       │
│ Bridge     │   │ Bridge     │
└──────┬─────┘   └─────┬──────┘
       │                │
┌──────▼─────┐   ┌─────▼──────┐
│ Brave      │   │ OpenTel    │
│ (Zipkin)   │   │ (Standard) │
└──────┬─────┘   └─────┬──────┘
       │                │
       └────────┬────────┘
                │
┌───────────────▼────────────────┐
│ Span Exporters                  │
│ - Zipkin (HTTP/Kafka)          │
│ - OTLP (Jaeger/Tempo)          │
└────────────────────────────────┘
```

### 3.2 Why Micrometer?

**Vendor Lock-In Problem:**
```java
// Using Brave directly
import brave.Tracer;
import brave.Span;

Tracer tracer = ...; // ❌ Locked to Brave
Span span = tracer.nextSpan();

// To switch to OTel: rewrite EVERYTHING
```

**Micrometer Solution:**
```java
// Using Micrometer (abstraction)
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;

@Autowired
private Tracer tracer;  // ✅ Works with Brave OR OTel

// To switch: just change Maven dependency!
```

### 3.3 Spring Boot Auto-Instrumentation

| Component | Instrumented? | Span Type |
|-----------|--------------|-----------|
| `@RestController` | ✅ Yes | SERVER |
| `RestTemplate` | ✅ Yes (if @Bean) | CLIENT |
| `WebClient` | ✅ Yes (if @Bean) | CLIENT |
| `@KafkaListener` | ✅ Yes | CONSUMER |
| `KafkaTemplate` | ✅ Yes | PRODUCER |
| `@Async` | ⚠️ Needs traced executor | INTERNAL |
| `@Scheduled` | ✅ Yes | INTERNAL |
| JDBC | ⚠️ Needs datasource-proxy | CLIENT |
| Feign | ⚠️ Needs feign-micrometer | CLIENT |

---

## 4. Zipkin Internals

### 4.1 What Zipkin IS and ISN'T

**Zipkin IS:**
- Storage backend (MySQL, Elasticsearch, Cassandra)
- Query API
- Web UI for visualization

**Zipkin ISN'T:**
- An instrumentation library (that's Brave/OTel)
- A metrics system (that's Prometheus)
- An APM with alerting (that's Datadog/NewRelic)

**Mental Model:**
```
Your App (creates spans) → Zipkin (stores & displays)
[Brave/OTel/Micrometer]    [Storage + UI]
```

### 4.2 Architecture

```
┌─────────────────────────────────────┐
│ Your Services (send spans via HTTP) │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ Zipkin Collector                     │
│ - Receives spans (POST /api/v2/spans)│
│ - Validates format                   │
│ - Batches writes                     │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ Storage                              │
│ ┌──────────┐  ┌──────────┐         │
│ │ MySQL    │  │ Elastic  │         │
│ └──────────┘  └──────────┘         │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ Query API                            │
│ GET /api/v2/trace/{traceId}         │
│ GET /api/v2/traces?serviceName=...  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ Zipkin UI (React Web App)           │
└─────────────────────────────────────┘
```

### 4.3 Data Model

**Span (Zipkin v2 JSON):**
```json
{
  "traceId": "abc123",
  "id": "span456",
  "parentId": "span789",
  "name": "GET /orders",
  "kind": "SERVER",
  "timestamp": 1706544923000000,
  "duration": 245000,
  "localEndpoint": {
    "serviceName": "order-service",
    "ipv4": "10.0.1.42"
  },
  "tags": {
    "http.method": "GET",
    "http.status_code": "200"
  }
}
```

**Storage (Elasticsearch):**
```json
{
  "index": "zipkin:span-2024-01-29",
  "body": {...span...}
}
```

---

## 5. Request Lifecycle

### Complete Example: E-commerce Order

**Architecture:**
```
Browser → API Gateway → Order Service → [Inventory, Payment]
                            ↓
                         Kafka → Notification Service
```

**Step-by-Step:**

**1. Browser Request (No trace context yet)**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{"userId": "user123", "items": [...]}
```

**2. API Gateway (ROOT span created)**
```java
// Span created automatically:
{
  "traceId": "abc123",  // ← Generated here!
  "spanId": "root1",
  "parentId": null,     // ← ROOT
  "name": "POST /api/orders",
  "kind": "SERVER"
}
```

**3. Gateway → Order Service (CLIENT span)**
```java
// Spring auto-creates CLIENT span and injects header:
headers.set("traceparent", "00-abc123-client1-01");

// Request sent:
POST http://order-service/orders
traceparent: 00-abc123-client1-01
```

**4. Order Service (SERVER span)**
```java
// Extracts context from header:
// traceId: abc123 (inherited!)
// spanId: server1 (new)
// parentId: client1 (from header)

@PostMapping("/orders")
public Order create(@RequestBody OrderRequest req) {
    // Span already active in context
    return orderService.create(req);
}
```

**5. Order → Inventory (parallel calls)**
```java
CompletableFuture<Inventory> inv = inventoryClient.check(...);
CompletableFuture<Payment> pay = paymentClient.process(...);

// Both create CLIENT spans with same parent (server1)
// Execute in parallel!
```

**6. Kafka Event (PRODUCER span)**
```java
kafkaTemplate.send("order-created", event);

// Span created:
{
  "traceId": "abc123",
  "spanId": "producer1",
  "parentId": "server1",
  "kind": "PRODUCER",
  "timestamp": 1706544923450000,
  "duration": 5000  // 5ms (send only)
}

// Message headers:
traceparent: 00-abc123-producer1-01
```

**7. Kafka Consumer (CONSUMER span, async)**
```java
@KafkaListener(topics = "order-created")
public void handle(OrderEvent event) {
    // New span:
    {
      "traceId": "abc123",  // Same trace!
      "spanId": "consumer1",
      "parentId": "producer1",
      "kind": "CONSUMER",
      "timestamp": 1706544925000000  // 1.5s later!
    }
}
```

**Final Trace:**
```
Trace abc123 [500ms sync + 150ms async]
│
├─ API Gateway SERVER [500ms]
│  └─ Order Service CLIENT [460ms]
│     └─ Order Service SERVER [450ms]
│        ├─ Inventory CLIENT [210ms] (parallel)
│        ├─ Payment CLIENT [260ms] (parallel)
│        └─ Kafka PRODUCER [5ms]
│
└─ Notification CONSUMER [150ms] (1.5s later)
```

---

## 6. Configuration

### 6.1 Maven Dependencies

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Micrometer Tracing -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>
    
    <!-- Zipkin Reporter -->
    <dependency>
        <groupId>io.zipkin.reporter2</groupId>
        <artifactId>zipkin-reporter-brave</artifactId>
    </dependency>
    
    <!-- OPTIONAL: DB tracing -->
    <dependency>
        <groupId>net.ttddyy</groupId>
        <artifactId>datasource-proxy</artifactId>
        <version>1.9</version>
    </dependency>
</dependencies>
```

### 6.2 application.yml

```yaml
spring:
  application:
    name: order-service  # CRITICAL!
    
  zipkin:
    base-url: http://localhost:9411
    encoder: JSON_V2  # or PROTO3 (50% smaller)
    
management:
  tracing:
    enabled: true
    sampling:
      probability: 0.1  # 10% sampling
      
    baggage:
      enabled: true
      remote-fields: userId,tenantId
      correlation:
        enabled: true
        fields: userId,tenantId  # Add to MDC
        
logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
```

### 6.3 Java Configuration

```java
@Configuration
public class TracingConfig {
    
    // Custom sampler
    @Bean
    public Sampler customSampler() {
        return context -> {
            // Always sample errors
            if (context.hasError()) {
                return SamplingDecision.SAMPLE;
            }
            // 10% for everything else
            return Math.random() < 0.1 ? 
                SamplingDecision.SAMPLE : 
                SamplingDecision.NOT_SAMPLE;
        };
    }
    
    // Database tracing
    @Bean
    public DataSource dataSource(DataSource actual) {
        return ProxyDataSourceBuilder
            .create(actual)
            .listener(new TracingQueryExecutionListener())
            .build();
    }
    
    // Async executor with tracing
    @Bean
    public Executor taskExecutor(BeanFactory beanFactory) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.initialize();
        return new LazyTraceExecutor(beanFactory, executor);
    }
}
```

### 6.4 Sampling (CRITICAL!)

**Why it matters:**
```
10,000 req/sec × 5 spans/req = 50,000 spans/sec
50,000 spans × 1KB = 50 MB/sec
50 MB/sec × 3600 = 180 GB/hour

With 1% sampling:
500 spans/sec × 1KB = 500 KB/sec
500 KB/sec × 3600 = 1.8 GB/hour ✅
```

**Production Strategy:**
```yaml
# Base sampling: 1%
management.tracing.sampling.probability: 0.01

# Always sample:
# - Errors (5xx)
# - Critical endpoints (/payment, /checkout)
# - Premium users
# - Debug requests
```

**Custom Sampler:**
```java
@Component
public class ProductionSampler implements Sampler {
    
    private final Map<String, Double> rates = Map.of(
        "POST /api/payment", 1.0,     // 100%
        "POST /api/orders", 0.1,      // 10%
        "GET /api/products", 0.01,    // 1%
        "GET /health", 0.0            // 0%
    );
    
    @Override
    public SamplingDecision shouldSample(TraceContext ctx) {
        String endpoint = ctx.spanName();
        double rate = rates.getOrDefault(endpoint, 0.05);
        return Math.random() < rate ? 
            SamplingDecision.SAMPLE : 
            SamplingDecision.NOT_SAMPLE;
    }
}
```

### 6.5 What If Zipkin Is Down?

**Good News:** Your app continues normally!

**What happens:**
```
1. Spans created normally ✅
2. Spans queued in memory buffer
3. Reporter attempts send (fails)
4. Spans dropped after buffer full
5. Application unaffected ✅
```

**Configuration:**
```yaml
# Async reporter settings
zipkin:
  reporter:
    queuedMaxSpans: 1000       # Buffer size
    queuedMaxBytes: 5000000    # 5MB
    messageTimeout: 500ms      # Send timeout
    closeTimeout: 1s           # Shutdown timeout
```

**Health Check:**
```java
@Component
public class ZipkinHealthIndicator implements HealthIndicator {
    @Autowired private Sender zipkinSender;
    
    @Override
    public Health health() {
        try {
            zipkinSender.check();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("zipkin", "Unavailable")
                .build();
        }
    }
}
```

---

## 7. Instrumentation

### 7.1 Auto vs Manual

**Auto-Instrumented (FREE):**
```java
@GetMapping("/orders/{id}")
public Order get(@PathVariable String id) {
    // SERVER span created automatically
    
    Customer c = restTemplate.getForObject(
        "http://customer/..." , Customer.class
    );
    // CLIENT span created automatically
    
    return order;
}
```

**Manual Span:**
```java
@Service
public class OrderService {
    @Autowired private Tracer tracer;
    
    public void processOrder(Order order) {
        Span span = tracer.nextSpan()
            .name("processOrder")
            .kind(Span.Kind.INTERNAL)
            .tag("order.id", order.getId())
            .tag("user.tier", "premium")
            .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // Business logic
            validateOrder(order);
            applyDiscounts(order);
            save(order);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 7.2 When to Create Custom Spans

**DO create spans for:**
- Important business logic (order processing, payment)
- External API calls (not auto-instrumented)
- Slow operations (> 100ms)
- Batch item processing
- Complex async workflows

**DON'T create spans for:**
- Already auto-instrumented operations
- Trivial operations (< 1ms)
- High-frequency loops (creates noise)

### 7.3 Advanced Patterns

**Nested Spans:**
```java
public Order createOrder(OrderRequest req) {
    Span parent = tracer.nextSpan().name("createOrder").start();
    try (Tracer.SpanInScope ws = tracer.withSpan(parent)) {
        
        // Child span 1
        Span validation = tracer.nextSpan().name("validate").start();
        try (Tracer.SpanInScope ws2 = tracer.withSpan(validation)) {
            validate(req);
        } finally {
            validation.end();
        }
        
        // Child span 2
        Span persistence = tracer.nextSpan().name("save").start();
        try (Tracer.SpanInScope ws3 = tracer.withSpan(persistence)) {
            return save(req);
        } finally {
            persistence.end();
        }
    } finally {
        parent.end();
    }
}
```

**Async Operations:**
```java
public CompletableFuture<Result> processAsync(Request req) {
    Span parentSpan = tracer.currentSpan();
    
    return CompletableFuture.supplyAsync(() -> {
        Span asyncSpan = tracer.nextSpan(parentSpan.context())
            .name("asyncProcessing")
            .start();
        try (Tracer.SpanInScope ws = tracer.withSpan(asyncSpan)) {
            return doWork(req);
        } finally {
            asyncSpan.end();
        }
    }, tracingAwareExecutor);
}
```

---

## 8. Logging Correlation

### 8.1 MDC Magic

**Without Correlation:**
```
INFO  Processing order
ERROR Payment failed
INFO  Order completed
```

**With Correlation:**
```
INFO  [order-service,abc123,span456] Processing order
ERROR [payment-service,abc123,span789] Payment failed
INFO  [order-service,abc123,span456] Order completed
```

**Configuration:**
```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
```

**How it works:**
```java
// Micrometer automatically:
MDC.put("traceId", span.context().traceId());
MDC.put("spanId", span.context().spanId());

// Your log:
log.info("Processing order");

// Output:
// INFO [order-service,abc123,def456] Processing order

// On span end:
MDC.remove("traceId");
MDC.remove("spanId");
```

### 8.2 Baggage in Logs

```yaml
management:
  tracing:
    baggage:
      correlation:
        enabled: true
        fields: userId,tenantId
```

**Result:**
```
INFO [order-service,abc123,def456,userId=user123,tenantId=acme] Order processing
```

### 8.3 ELK Integration

**Logstash Pattern:**
```ruby
filter {
  grok {
    match => { 
      "message" => "\[%{DATA:service},%{DATA:traceId},%{DATA:spanId}\]" 
    }
  }
}
```

**Kibana Query:**
```
traceId: "abc123"
```
→ All logs from all services for this trace!

---

## 9. Debugging

### 9.1 Traces Not Appearing

**Checklist:**

1. **Zipkin running?**
   ```bash
   curl http://localhost:9411/health
   ```

2. **Sampling > 0?**
   ```yaml
   management.tracing.sampling.probability: 1.0  # Test
   ```

3. **Dependencies correct?**
   ```xml
   <!-- Need BOTH -->
   <dependency>
     <groupId>io.micrometer</groupId>
     <artifactId>micrometer-tracing-bridge-brave</artifactId>
   </dependency>
   <dependency>
     <groupId>io.zipkin.reporter2</groupId>
     <artifactId>zipkin-reporter-brave</artifactId>
   </dependency>
   ```

4. **Service name set?**
   ```yaml
   spring.application.name: my-service
   ```

5. **Debug logging:**
   ```yaml
   logging.level.zipkin2: DEBUG
   ```

### 9.2 Missing Spans

**RestTemplate not instrumented:**
```java
// ❌ Wrong
private RestTemplate restTemplate = new RestTemplate();

// ✅ Correct
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

**JDBC not instrumented:**
```java
@Bean
public DataSource dataSource(DataSource actual) {
    return ProxyDataSourceBuilder.create(actual)
        .listener(new TracingQueryExecutionListener())
        .build();
}
```

**Async context lost:**
```java
// ❌ Wrong
@Async
public void work() {
    Span span = tracer.currentSpan();  // NULL!
}

// ✅ Correct
@Bean
public Executor executor(BeanFactory bf) {
    return new LazyTraceExecutor(bf, ...);
}
```

### 9.3 Broken Trace Chains

**Cause:** Trace context not propagated

**Wrong:**
```java
HttpClient client = HttpClient.newHttpClient();
client.send(request, ...);  // No headers!
```

**Correct:**
```java
@Autowired
private RestTemplate restTemplate;
restTemplate.getForObject(...);  // Headers added!
```

**Load Balancer Issue:**
```nginx
# Nginx must forward headers
proxy_set_header traceparent $http_traceparent;
```

---

## 10. Production Best Practices

### 10.1 Sampling Strategy

```java
@Component
public class ProductionSampler implements Sampler {
    @Override
    public SamplingDecision shouldSample(TraceContext ctx) {
        // Always sample errors
        if (ctx.hasError()) return SamplingDecision.SAMPLE;
        
        // Always sample critical endpoints
        if (ctx.spanName().contains("payment")) {
            return SamplingDecision.SAMPLE;
        }
        
        // Sample premium users 50%
        String tier = ctx.getBaggage("user.tier");
        if ("premium".equals(tier)) {
            return Math.random() < 0.5 ? 
                SamplingDecision.SAMPLE : 
                SamplingDecision.NOT_SAMPLE;
        }
        
        // Sample 1% of regular traffic
        return Math.random() < 0.01 ? 
            SamplingDecision.SAMPLE : 
            SamplingDecision.NOT_SAMPLE;
    }
}
```

### 10.2 Security

**DON'T:**
```java
span.tag("user.email", email);        // ❌ PII
span.tag("credit.card", cardNumber);  // ❌ PCI violation
span.tag("password", pwd);            // ❌ NEVER
```

**DO:**
```java
span.tag("user.id", hashId(email));   // ✅ Hashed
span.tag("payment.method", "card");   // ✅ Type only
```

### 10.3 High-Throughput

**For 100K+ req/sec:**

1. **Aggressive sampling (0.1%)**
   ```yaml
   management.tracing.sampling.probability: 0.001
   ```

2. **Kafka transport (avoid HTTP bottleneck)**
   ```yaml
   spring.zipkin.sender.type: kafka
   ```

3. **Large buffers**
   ```java
   AsyncReporter.builder(sender)
       .queuedMaxSpans(50000)
       .queuedMaxBytes(50_000_000)
       .build();
   ```

4. **Head-based sampling at gateway**
   ```java
   // Sample once at entry point
   // All downstream services inherit decision
   ```

### 10.4 When NOT to Trace

**Disable for:**
```java
@Bean
public SkipPatternProvider skipPattern() {
    return () -> Pattern.compile(
        "/health|/metrics|/actuator.*"
    );
}
```

**Sample lightly:**
- Health checks: 0%
- Static assets: 0%
- Background jobs: 0.1%
- Cache hits: 1%

### 10.5 Kubernetes

**Zipkin Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zipkin
spec:
  template:
    spec:
      containers:
      - name: zipkin
        image: openzipkin/zipkin:latest
        env:
        - name: STORAGE_TYPE
          value: elasticsearch
        - name: ES_HOSTS
          value: http://elasticsearch:9200
        resources:
          requests:
            memory: 1Gi
            cpu: 500m
```

**Service Mesh (Istio):**
```yaml
# Istio config
tracing:
  zipkin:
    address: zipkin.istio-system:9411
  sampling: 10.0
```

**Your app still needs Micrometer for:**
- Internal spans
- Database tracing
- Custom business logic

---

## Key Takeaways

### The Fundamentals
1. **Trace** = Complete request journey (one ID across all services)
2. **Span** = Single operation (timed unit of work)
3. **Context Propagation** = Headers carry traceId/spanId
4. **Sampling** = Don't trace 100% (costs money/performance)

### The Architecture
```
Micrometer (API) → Brave/OTel (Impl) → Zipkin (Storage/UI)
```

### Production Checklist
- [ ] Sampling ≤ 10% (preferably ≤ 1%)
- [ ] Always sample errors
- [ ] Trace IDs in logs (MDC)
- [ ] No PII in spans
- [ ] Zipkin health monitoring
- [ ] Auto-instrumentation (RestTemplate, WebClient, Kafka)
- [ ] Manual spans for business logic
- [ ] Async executor tracing
- [ ] Database query tracing (if needed)

### Performance Impact
```
Overhead: < 1% CPU, < 200μs per request
Memory: ~800 bytes per span
Network: ~1KB per span to Zipkin
```

### When Things Go Wrong
1. Check Zipkin health
2. Verify sampling > 0
3. Enable debug logging
4. Test with `probability: 1.0`
5. Validate service name set
6. Check auto-instrumentation (@Bean for RestTemplate)

---

## Further Reading

- **Micrometer Docs:** https://micrometer.io/docs/tracing
- **Zipkin Architecture:** https://zipkin.io/pages/architecture.html
- **OpenTelemetry Semantic Conventions:** https://opentelemetry.io/docs/specs/semconv/
- **Spring Boot Observability:** https://spring.io/blog/2022/10/12/observability-with-spring-boot-3

---

**You now have production-grade mastery of distributed tracing. Go build observable systems! 🚀**