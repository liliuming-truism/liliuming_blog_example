# Design Patterns – Java Recipes

Quick-reference implementations for patterns commonly needed during Java refactoring.

---

## Builder Pattern

**Use when**: Constructor has >3 params, or many optional fields.

```java
// Before
new Order(userId, product, qty, null, null, true);

// After
Order order = Order.builder()
    .userId(userId)
    .product(product)
    .quantity(qty)
    .express(true)
    .build();

// Implementation (Lombok)
@Builder
@Value
public class Order {
    String userId;
    Product product;
    int quantity;
    String couponCode;   // optional
    Address address;     // optional
    boolean express;
}
```

---

## Strategy Pattern

**Use when**: `if/else` or `switch` selects algorithm at runtime.

```java
// Before
if (paymentType.equals("CARD")) { ... }
else if (paymentType.equals("PAYPAL")) { ... }
else if (paymentType.equals("CRYPTO")) { ... }

// After
public interface PaymentStrategy {
    void pay(BigDecimal amount);
}

@Component("CARD")  public class CardPayment  implements PaymentStrategy { ... }
@Component("PAYPAL") public class PayPalPayment implements PaymentStrategy { ... }

// Injecting all strategies as a map
private final Map<String, PaymentStrategy> strategies;

public PaymentService(Map<String, PaymentStrategy> strategies) {
    this.strategies = strategies;
}

public void process(String type, BigDecimal amount) {
    strategies.getOrDefault(type, unsupportedStrategy).pay(amount);
}
```

---

## Template Method

**Use when**: Multiple classes share the same algorithm skeleton with different steps.

```java
// Abstract base
public abstract class ReportGenerator {
    public final String generate(ReportRequest req) {  // final = don't override skeleton
        var data = fetchData(req);
        var formatted = format(data);
        return addHeader(formatted);
    }

    protected abstract List<Row> fetchData(ReportRequest req);
    protected abstract String format(List<Row> rows);

    private String addHeader(String body) { return "REPORT\n" + body; }
}

public class CsvReportGenerator extends ReportGenerator { ... }
public class PdfReportGenerator extends ReportGenerator { ... }
```

---

## Records (Java 16+)

**Use when**: Class is a pure data carrier with no mutable state.

```java
// Before
@Data
@AllArgsConstructor
public class Point {
    private double x;
    private double y;
}

// After
public record Point(double x, double y) {}

// With validation
public record Point(double x, double y) {
    public Point {
        if (x < 0 || y < 0) throw new IllegalArgumentException("Coordinates must be non-negative");
    }
}
```

---

## Guard Clauses (Early Return)

**Use when**: Multiple nested `if` blocks guard the "happy path".

```java
// Before
public Result process(Request req) {
    if (req != null) {
        if (req.isValid()) {
            if (req.hasPermission()) {
                return doWork(req);
            } else {
                throw new AccessDeniedException();
            }
        } else {
            throw new ValidationException();
        }
    } else {
        throw new IllegalArgumentException();
    }
}

// After
public Result process(Request req) {
    Objects.requireNonNull(req, "Request must not be null");
    if (!req.isValid())      throw new ValidationException();
    if (!req.hasPermission()) throw new AccessDeniedException();
    return doWork(req);
}
```

---

## Sealed Classes + Pattern Matching (Java 17+)

**Use when**: `instanceof` chains or enums with associated data.

```java
// Before
if (shape instanceof Circle) {
    Circle c = (Circle) shape;
    return Math.PI * c.getRadius() * c.getRadius();
} else if (shape instanceof Rectangle) { ... }

// After
public sealed interface Shape permits Circle, Rectangle, Triangle {}
public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}

public double area(Shape shape) {
    return switch (shape) {
        case Circle c    -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
        case Triangle t  -> 0.5 * t.base() * t.height();
    };
}
```