# Spring Boot Modernization Guide

Targeting Spring Boot 3.x / Java 17+. Apply these patterns when refactoring Spring code.

---

## Constructor Injection (over @Autowired)

```java
// Before
@Service
public class OrderService {
    @Autowired
    private OrderRepository repo;
    @Autowired
    private EmailService emailService;
}

// After
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class OrderService {
    private final OrderRepository repo;
    private final EmailService emailService;
}
// Or without Lombok:
public class OrderService {
    private final OrderRepository repo;
    private final EmailService emailService;

    public OrderService(OrderRepository repo, EmailService emailService) {
        this.repo = repo;
        this.emailService = emailService;
    }
}
```
**Why**: Makes dependencies explicit, enables easy unit testing without Spring context, immutable fields.

---

## @ConfigurationProperties Record (Spring Boot 3+)

```java
// Before
@Value("${app.mail.host}")   private String host;
@Value("${app.mail.port}")   private int port;
@Value("${app.mail.sender}") private String sender;

// After — application.yml: app.mail.host/port/sender
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String host, int port, String sender) {}

// In @SpringBootApplication or config class:
@EnableConfigurationProperties(MailProperties.class)
```

---

## RestClient (Spring Boot 3.2+, replaces RestTemplate)

```java
// Before
RestTemplate rt = new RestTemplate();
ResponseEntity<User> resp = rt.getForEntity(url + "/users/" + id, User.class);
User user = resp.getBody();

// After
@Bean
public RestClient restClient(RestClient.Builder builder) {
    return builder.baseUrl("https://api.example.com").build();
}

// Usage
User user = restClient.get()
    .uri("/users/{id}", id)
    .retrieve()
    .body(User.class);
```

---

## Mapping Shortcuts

```java
// Before
@RequestMapping(value = "/users", method = RequestMethod.GET)
@RequestMapping(value = "/users/{id}", method = RequestMethod.DELETE)

// After
@GetMapping("/users")
@DeleteMapping("/users/{id}")
```

---

## Optional Handling

```java
// Before
Optional<User> opt = repo.findById(id);
if (opt.isPresent()) {
    return opt.get();
} else {
    throw new NotFoundException();
}

// After
return repo.findById(id)
    .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
```

---

## Pagination (prevent unbounded queries)

```java
// Before
List<Order> orders = repo.findByUserId(userId);  // dangerous at scale

// After — repository
Page<Order> findByUserId(String userId, Pageable pageable);

// Controller
@GetMapping("/users/{id}/orders")
public Page<Order> getOrders(
    @PathVariable String id,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
    return orderService.getOrders(id, PageRequest.of(page, size));
}
```

---

## @Transactional Placement

```java
// Before — on repository (wrong)
@Repository
@Transactional
public interface OrderRepository extends JpaRepository<Order, Long> {}

// Before — on controller (wrong)
@Transactional
@PostMapping("/orders")
public ResponseEntity<Order> create(...) {}

// After — on service layer (correct)
@Service
public class OrderService {
    @Transactional
    public Order createOrder(CreateOrderRequest req) { ... }

    @Transactional(readOnly = true)
    public List<Order> getOrders(String userId) { ... }
}
```

---

## ControllerAdvice for Global Error Handling

```java
// Before — try/catch in every controller
@PostMapping("/orders")
public ResponseEntity<?> create(...) {
    try { ... }
    catch (NotFoundException e) { return ResponseEntity.notFound().build(); }
    catch (Exception e) { return ResponseEntity.internalServerError().build(); }
}

// After
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleValidation(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
// Controllers are now clean — just call service, return result
```