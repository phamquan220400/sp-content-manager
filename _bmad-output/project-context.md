---
project_name: 'spring_project'
user_name: 'Samuel'
date: '2026-04-25'
sections_completed: ['discovery']
existing_patterns_found: 15
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

**Core Platform:**
- Java 17 with Spring Boot 3.3.5
- MySQL 8.0 (production) / H2 (testing)
- Redis for caching and session management
- Maven build system

**Key Dependencies:**
- Spring Boot: Web, JPA, Security, Validation, Mail, Actuator
- JWT: io.jsonwebtoken:jjwt-* v0.12.6
- Database: Flyway migrations, MySQL Connector J
- Caching: Spring Cache with Caffeine (in-process TTL)
- Development: Lombok, Spring Boot DevTools
- Testing: Spring Boot Test, Spring Security Test, JUnit 5, Mockito

**Architecture Characteristics:**
- Platform-agnostic creator economy SaaS platform
- Circuit breaker patterns for external API resilience
- Multi-tenant architecture with compliance audit logging
- Domain-driven design with layered architecture

---

## Critical Implementation Rules

_Ready for collaborative context generation_