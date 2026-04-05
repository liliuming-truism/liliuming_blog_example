# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A multi-module Maven project serving as a coding knowledge repository (blog companion code). Java 21, Spring Boot 3.5.0, Spring Framework 6.2.7.

## Build & Test Commands

```bash
# Build entire project
mvn clean package

# Build a specific module
mvn -pl spring-aop clean package

# Run all tests
mvn clean test

# Run tests for a specific module
mvn -pl spring-aop test

# Run a Spring Boot module
mvn -pl spring-aop spring-boot:run
mvn -pl best-practice spring-boot:run
```

> `best-practice` requires a running Redis instance (configured in `best-practice/src/main/resources/application.yml`).

## Module Structure

| Module | Purpose |
|---|---|
| `spring-ioc` | Spring BeanDefinition lifecycle, dynamic bean registration |
| `spring-aop` | Spring AOP pointcut expressions (execution, within, args, target, this, annotation-based) and CGLIB proxy demos |
| `spring-annotation` | Spring `MergedAnnotations` API for annotation introspection |
| `spring-web` | Placeholder Spring Boot web module (no implementation yet) |
| `jdk-core` | JDK 9 VarHandle (atomic ops, memory modes) and JDK 14 Records |
| `best-practice` | Distributed cache refresh patterns using Redis and Redisson |
| `truism-log` | Custom Spring Boot auto-configuration starter for structured Log4j2/JSON logging with trace ID propagation |
| `leetcodeed` | LeetCode algorithm solutions |
| `readnotes` | Data structure implementations (e.g., AVL tree) |

## Architecture Notes

### Package conventions
All source lives under `top.truism.blog.<module-specific-path>`. Each module maps to one technical topic; classes within a module are organized by sub-topic (e.g., `spring-aop` has sub-packages `pointcut/execution`, `pointcut/within`, `pointcut/args`, `proxy/cglib`, etc.).

### truism-log starter
`truism-log/truism-log-starter` is a Spring Boot auto-configuration starter. It:
- Excludes Logback and replaces it with Log4j2 + JSON template layout
- Provides `TraceIdFilter` for servlet-based trace ID propagation
- Bootstraps via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Configuration properties bound from `LoggingProperties` (prefix in `application.yml`)

### best-practice cache refresh
Three implementations of distributed cache refresh:
- `DistributedCacheRefreshManager` — Redis SET NX/EX-based locking with Lua scripts for atomic operations
- `DualTriggerCacheRefreshManager` — dual-trigger with debouncing via `ScheduledExecutorService`
- `RedissonCacheRefreshManager` — same pattern using Redisson's `RLock`

### spring-aop examples
Each pointcut type has its own sub-package with a `*Application.java` Spring Boot entry point. Aspects and services are co-located in the same package for readability.
