# Common Spring Boot Anti-Patterns & Fixes

## Anti-Pattern 1: Anemic Service Layer
**Problem**: Service just delegates to repository, all logic in controller or entity.
**Fix**: Services should contain business rules; controllers only handle HTTP I/O.

## Anti-Pattern 2: God Service
**Problem**: `UserService` handles users, orders, payments, notifications — 2000 lines.
**Fix**: Split by domain. `UserService`, `OrderService`, `NotificationService` etc.

## Anti-Pattern 3: @Transactional on Everything
**Problem**: `@Transactional` on every method including simple reads and utility methods.
**Fix**: `@Transactional(readOnly=true)` on reads; only wrap actual multi-step writes.

## Anti-Pattern 4: Returning JPA Entities from REST
**Problem**: Exposes internal DB schema, causes lazy-load exceptions, leaks sensitive fields.
**Fix**: Always map to DTOs before returning from controllers.

## Anti-Pattern 5: Ignoring Validation
**Problem**: Request body accepted with no validation — nulls, negative IDs, blank strings.
**Fix**: `@Valid` on `@RequestBody`, Bean Validation annotations (`@NotNull`, `@Size`, `@Email`).

## Anti-Pattern 6: Exception Swallowing
```java
// BAD
try {
    orderService.placeOrder(req);
} catch (Exception e) {
    // silent
}
```
**Fix**: Always log at minimum. Re-throw or handle meaningfully.

## Anti-Pattern 7: Hardcoded Configuration
```java
// BAD
RestTemplate rt = new RestTemplate();
rt.setRequestFactory(factory); // timeout hardcoded somewhere
String url = "http://payment-service:8080"; // hardcoded
```
**Fix**: `@ConfigurationProperties`, environment variables, `application.yml`.

## Anti-Pattern 8: Missing Index on Queried Columns
**Problem**: `findByEmail()` without index on `email` column → full table scan.
**Fix**: `@Index` on `@Column` or via Flyway/Liquibase migration.

## Anti-Pattern 9: Lazy vs Eager Loading Confusion
**Problem**: `FetchType.EAGER` on collections causes huge joins; `FetchType.LAZY` causes N+1.
**Fix**: Default to LAZY. Use `JOIN FETCH` in JPQL when you need related data.

## Anti-Pattern 10: No API Versioning
**Problem**: Breaking changes deployed to `/api/users` with no versioning strategy.
**Fix**: Version from day 1: `/api/v1/users`. Deprecate old versions gracefully.