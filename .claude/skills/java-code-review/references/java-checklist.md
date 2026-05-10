# Java Backend Code Review — Full Checklist

## Table of Contents
1. [Code Structure & Design](#1-code-structure--design)
2. [Java Language Best Practices](#2-java-language-best-practices)
3. [Spring Boot Patterns](#3-spring-boot-patterns)
4. [Security](#4-security)
5. [Performance & Scalability](#5-performance--scalability)
6. [Error Handling](#6-error-handling)
7. [Concurrency](#7-concurrency)
8. [Testing](#8-testing)
9. [Observability](#9-observability)

---

## 1. Code Structure & Design

### SOLID Principles
- **SRP**: Each class has one reason to change. A `UserService` shouldn't also send emails — that's `EmailService`.
- **OCP**: Extend via new classes/interfaces, not by modifying existing ones.
- **LSP**: Subclasses honour the contract of their parent. Be careful with `@Override`.
- **ISP**: Interfaces should be small and focused — don't force clients to implement methods they don't need.
- **DIP**: Depend on abstractions (`UserRepository`) not concretions (`JpaUserRepository`).

### Package Structure
- Prefer **feature packages** (`com.app.user`, `com.app.order`) over **layer packages** (`com.app.service`, `com.app.repository`)
- Avoid circular dependencies between packages

### Class Design
- Prefer composition over inheritance
- Keep classes small (<300 lines is a guideline)
- `final` on classes that shouldn't be subclassed
- Immutable value objects where appropriate (records in Java 16+)

---

## 2. Java Language Best Practices

### Optionals
```java
// BAD
Optional<User> user = repo.findById(id);
if (user.isPresent()) return user.get();

// GOOD
return repo.findById(id).orElseThrow(() -> new UserNotFoundException(id));
```
- Never call `.get()` without `.isPresent()` check
- Don't use `Optional` as method parameters or fields — only return types

### Streams
```java
// BAD — side effects in stream
users.stream().forEach(u -> { u.setActive(true); userRepo.save(u); });

// GOOD — keep streams pure, do mutations outside
List<User> toActivate = users.stream().filter(...).collect(toList());
    userRepo.saveAll(toActivate);
```

### Collections
- Always use interface types: `List<>`, `Map<>`, `Set<>` — not `ArrayList<>` etc.
- Prefer `List.of()`, `Map.of()` for immutable collections (Java 9+)
- `Collections.unmodifiableList()` for defensive copies

### String Handling
- Use `String.format()` or text blocks (Java 15+) for multi-line strings
- `StringBuilder` inside loops, never `+` concatenation in loops
- Never compare strings with `==`

### Resource Management
```java
// Always use try-with-resources
try (InputStream in = new FileInputStream(file)) {
    // ...
    }
```

---

## 3. Spring Boot Patterns

### Injection
```java
// BAD — field injection
@Autowired
private UserService userService;

// GOOD — constructor injection (testable, immutable)
private final UserService userService;

public UserController(UserService userService) {
    this.userService = userService;
}
```

### Transaction Management
```java
// BAD — @Transactional on repository method
// BAD — @Transactional on controller
// GOOD — @Transactional on service method

@Service
public class OrderService {
    @Transactional
    public Order placeOrder(OrderRequest req) { ... }

    @Transactional(readOnly = true)  // ← readOnly for reads — better performance
    public Order getOrder(Long id) { ... }
}
```
- Watch for `@Transactional` self-invocation problem (proxy bypass)
- Understand propagation: `REQUIRED` (default), `REQUIRES_NEW`, `NESTED`

### Controller Layer
```java
// BAD — business logic in controller
@PostMapping("/users")
public ResponseEntity<User> create(@RequestBody User user) {
    if (userRepo.existsByEmail(user.getEmail())) throw new RuntimeException("exists");
    user.setPassword(bcrypt.encode(user.getPassword())); // ← logic belongs in service
    return ResponseEntity.ok(userRepo.save(user));
}

// GOOD — controller only does HTTP concerns
@PostMapping("/users")
public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req) {
    return ResponseEntity.status(201).body(userService.createUser(req));
}
```

### DTO Pattern
- Use separate request/response DTOs — never expose JPA entities directly to API
- Validate input DTOs with `@Valid` + Bean Validation annotations
- Use MapStruct or manual mapping — avoid mapping in controllers

### Configuration
- Externalize config to `application.yml` / environment variables
- Use `@ConfigurationProperties` for typed config binding
- Profile-specific configs: `application-prod.yml`, `application-dev.yml`
- Never hardcode hostnames, credentials, or timeouts

---

## 4. Security

### SQL Injection
```java
// CRITICAL BUG — SQL injection
String query = "SELECT * FROM users WHERE name = '" + name + "'";
em.createNativeQuery(query); // ← NEVER DO THIS

// SAFE — parameterized
em.createQuery("SELECT u FROM User u WHERE u.name = :name")
  .setParameter("name", name);
```

### Authentication & Authorization
- Always validate JWT signatures server-side
- Use `@PreAuthorize("hasRole('ADMIN')")` for method-level security
- Don't trust user-supplied IDs for ownership checks — verify in DB
- Rate-limit authentication endpoints

### Password Handling
```java
// BAD
user.setPassword(password); // plain text!
user.setPassword(md5(password)); // broken hash

// GOOD
    user.setPassword(passwordEncoder.encode(password)); // BCrypt
```

### Sensitive Data
- Never log passwords, tokens, SSNs, credit card numbers
- Use `@JsonIgnore` on sensitive fields
- Mask PII in logs: `log.info("User {} logged in", maskEmail(email))`

### HTTPS / Headers
- Force HTTPS in production
- Set security headers: `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy`
- Configure CORS to specific origins only

---

## 5. Performance & Scalability

### JPA / N+1 Problem
```java
// BAD — N+1: 1 query for orders + N queries for each user
List<Order> orders = orderRepo.findAll();
orders.forEach(o -> log.info(o.getUser().getName())); // triggers N lazy loads

// GOOD — single JOIN FETCH query
@Query("SELECT o FROM Order o JOIN FETCH o.user")
List<Order> findAllWithUsers();
```

### Pagination
```java
// BAD — loads all records
List<Product> products = productRepo.findAll();

// GOOD — paginated
Page<Product> products = productRepo.findAll(PageRequest.of(page, size));
```

### Caching
```java
@Cacheable(value = "products", key = "#id")
public Product getProduct(Long id) { ... }

@CacheEvict(value = "products", key = "#product.id")
public Product updateProduct(Product product) { ... }
```

### Async Processing
```java
// For long-running tasks, don't block the request thread
@Async
public CompletableFuture<Void> sendWelcomeEmail(User user) { ... }
```

---

## 6. Error Handling

### Global Exception Handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(UserNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Extract field errors and return 400
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex); // log full stack trace internally
        return ResponseEntity.status(500).body(new ErrorResponse("Internal server error")); // never expose stack trace to client
    }
}
```

### Custom Exceptions
- Prefer typed exceptions over generic `RuntimeException`
- Include relevant context: `new OrderNotFoundException("Order not found: " + orderId)`
- Checked vs unchecked: use checked for recoverable conditions, unchecked for programming errors

---

## 7. Concurrency

### Common Issues
- **Race condition**: multiple threads updating shared state without synchronization
- **Deadlock**: two threads waiting for each other's locks
- **Visibility**: changes made by one thread not seen by another (use `volatile` or `AtomicXxx`)

### Thread-Safe Patterns
```java
// BAD — not thread safe
private int counter = 0;
public void increment() { counter++; }

// GOOD
private final AtomicInteger counter = new AtomicInteger(0);
public void increment() { counter.incrementAndGet(); }
```

- Prefer `ConcurrentHashMap` over `HashMap` in shared state
- Use `@Async` + thread pool configuration for background tasks
- Avoid `synchronized` on public methods — it's a scalability bottleneck
- `ThreadLocal` is dangerous with thread pools — always clean up with `remove()`

---

## 8. Testing

### Unit Tests
```java
// Good unit test structure: Arrange / Act / Assert
@Test
void shouldThrowWhenUserNotFound() {
    // Arrange
    when(userRepo.findById(99L)).thenReturn(Optional.empty());

    // Act + Assert
    assertThrows(UserNotFoundException.class, () -> userService.getUser(99L));
}
```
- Test behaviour, not implementation
- One logical assertion per test (but can have multiple `assertThat` on same object)
- Test names describe the scenario: `should_returnEmpty_when_userIsInactive()`

### Integration Tests
```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIT {
    @Test
    void createUser_returnsCreated() throws Exception {
        mockMvc.perform(post("/users").contentType(JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }
}
```

### Test Coverage Rules of Thumb
- Business logic (services): aim for 80%+
- Controllers: integration tests > unit tests
- Repositories: Spring Data tests with `@DataJpaTest`
- Utils/helpers: 100% if purely functional

---

## 9. Observability

### Logging
```java
// BAD
System.out.println("User created: " + user); // never use System.out
log.info("User created: " + user.getEmail()); // string concat — lazy eval lost

// GOOD
    log.info("User created: {}", user.getEmail()); // SLF4J parameterized
```

- Use structured logging (JSON) in production
- Log at appropriate level: DEBUG for flow, INFO for significant events, WARN for recoverable issues, ERROR for failures
- Include correlation IDs / trace IDs in logs (MDC)

### Metrics & Tracing
- Expose `/actuator/health`, `/actuator/metrics` in Spring Boot
- Add `@Timed` or Micrometer annotations to critical paths
- Use distributed tracing (Sleuth / OpenTelemetry) for microservices