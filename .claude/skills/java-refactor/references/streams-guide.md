# Java Streams – When to Use & Common Patterns

---

## Use Streams When…
- Transforming one collection into another (`map`, `filter`, `flatMap`)
- Aggregating/reducing a collection to a single value
- The pipeline is 3+ steps that read naturally left-to-right
- Collecting into a different container type

## Use Loops When…
- You need to `break` or `continue` mid-iteration
- You're mutating external state (streams discourage side effects)
- The logic is complex enough that a stream is harder to read than a loop
- Performance is critical and the collection is small (streams have overhead)

---

## Common Patterns

### Filter + Map + Collect
```java
// Before
List<String> result = new ArrayList<>();
for (User u : users) {
    if (u.isActive()) {
        result.add(u.getName().toUpperCase());
    }
}

// After
List<String> result = users.stream()
    .filter(User::isActive)
    .map(u -> u.getName().toUpperCase())
    .toList();  // Java 16+ (unmodifiable); use .collect(Collectors.toList()) for mutable
```

### Group By
```java
// Group users by department
Map<String, List<User>> byDept = users.stream()
    .collect(Collectors.groupingBy(User::getDepartment));
```

### Partition
```java
// Split into active/inactive
Map<Boolean, List<User>> split = users.stream()
    .collect(Collectors.partitioningBy(User::isActive));
List<User> active   = split.get(true);
List<User> inactive = split.get(false);
```

### FlatMap (nested collections)
```java
// All orders across all users
List<Order> allOrders = users.stream()
    .flatMap(u -> u.getOrders().stream())
    .toList();
```

### Find / Match
```java
Optional<User> admin = users.stream()
    .filter(u -> u.getRole() == Role.ADMIN)
    .findFirst();

boolean anyActive = users.stream().anyMatch(User::isActive);
boolean allValid  = users.stream().allMatch(User::isValid);
```

### Reduce / Sum
```java
// Total order value
BigDecimal total = orders.stream()
    .map(Order::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// Count
long activeCount = users.stream().filter(User::isActive).count();
```

### toMap
```java
// Build a lookup map id → user
Map<Long, User> byId = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));

// With merge function (handle duplicate keys)
Map<String, User> byEmail = users.stream()
    .collect(Collectors.toMap(User::getEmail, u -> u, (a, b) -> a));
```

### String joining
```java
String csv = names.stream().collect(Collectors.joining(", "));
```

---

## Performance Notes

- Parallel streams (`parallelStream()`) only help for CPU-bound work on large collections (>10k elements). Don't use by default.
- Prefer `.toList()` (Java 16+) over `.collect(Collectors.toList())` — it's unmodifiable and cleaner.
- Avoid calling `.stream()` inside a stream pipeline (nested streams) — flatten with `flatMap` instead.