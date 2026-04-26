---
project_name: 'spring_project'
user_name: 'Samuel'
date: '2026-04-25'
sections_completed: ['discovery', 'language_specific_rules']
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

### Java/Spring Boot Language-Specific Rules

#### Multi-Tenant Context Management

**✅ REQUIRED: Use TenantContextHolder Pattern**
```java
@Component
public class TenantContextHolder {
    private static final ThreadLocal<String> tenantId = new ThreadLocal<>();
    
    public static void setTenant(String tenantId) { 
        TenantContextHolder.tenantId.set(tenantId); 
    }
    public static String getCurrentTenant() { 
        return tenantId.get(); 
    }
    public static void clear() { 
        tenantId.remove(); 
    }
}

// All service methods must use tenant context
@Service
public class RevenueService {
    public void processRevenue(RevenueData data) {
        String tenantId = TenantContextHolder.getCurrentTenant();
        // Process with tenant isolation
    }
}
```

**❌ FORBIDDEN: Direct queries without tenant context**
```java
// Missing tenant isolation - data leakage risk
revenueRepository.findByUserId(userId); // WRONG
```

#### Constructor Injection Enforcement

**✅ REQUIRED: Constructor injection only**
```java
@Service
public class AuthService {
    private final AuthenticationManager authManager;
    private final Clock clock;
    
    // Single constructor - Spring auto-wires
    public AuthService(AuthenticationManager authManager, Clock clock) {
        this.authManager = authManager;
        this.clock = clock;
    }
}
```

**❌ FORBIDDEN: Field injection**
```java
@Service  
public class AuthService {
    @Autowired private AuthenticationManager authManager; // WRONG
    @Autowired private Clock clock; // WRONG
}
```

#### JPA Entity Patterns

**✅ REQUIRED: Explicit naming and tenant-aware entities**
```java
@Entity
@Table(name = "creator_profiles")
public class CreatorProfile extends TenantAwareEntity {
    
    @Column(name = "display_name")
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "creator_category") 
    private CreatorCategory creatorCategory;
    
    @Column(name = "content_preferences", columnDefinition = "JSON")
    private String contentPreferences;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

@MappedSuperclass
public abstract class TenantAwareEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @PrePersist
    public void setTenantId() {
        if (tenantId == null) {
            tenantId = TenantContextHolder.getCurrentTenant();
        }
    }
}
```

#### Circuit Breaker Implementation Patterns

**✅ REQUIRED: Platform adapter circuit breaker pattern**
```java
@Component
@CircuitBreaker(name = "youtube-api", fallbackMethod = "fallbackToCache")
@RateLimiter(name = "youtube-api")
@Retry(name = "youtube-api")
public class YouTubeAdapter implements PlatformConnector {
    
    @Override
    public CompletableFuture<PlatformMetrics> getMetrics(String creatorId) {
        // Platform API call with automatic retry and circuit breaker
    }
    
    public PlatformMetrics fallbackToCache(String creatorId, Exception ex) {
        log.warn("YouTube API failure, falling back to cache: {}", ex.getMessage());
        return cachedMetricsService.getLastKnownMetrics(creatorId);
    }
    
    @Override
    public boolean isHealthy() {
        return circuitBreakerRegistry
            .circuitBreaker("youtube-api")
            .getState() == CircuitBreaker.State.CLOSED;
    }
}
```

#### Authentication Context Pattern

**✅ REQUIRED: Centralized authentication helper**
```java
@Component  
public class AuthenticationHelper {
    public String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
    
    public String getCurrentTenantId() {
        // Extract tenant from JWT claims or context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("tenant_id");
        }
        return TenantContextHolder.getCurrentTenant();
    }
}
```

**❌ FORBIDDEN: Direct SecurityContext access**
```java
// Don't do this in services/controllers
String userId = SecurityContextHolder.getContext().getAuthentication().getName(); // WRONG
```

#### Record DTO Validation Patterns

**✅ REQUIRED: Record DTOs with complete validation**
```java
public record CreateProfileRequest(
    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be 100 characters or less")
    String displayName,
    
    @NotNull(message = "Category is required")
    @Pattern(regexp = "GAMING|LIFESTYLE|BUSINESS|EDUCATION", 
             message = "Invalid creator category")
    String creatorCategory,
    
    @Valid @NotNull
    List<@Valid PlatformConnection> platforms
) {}
```

#### Test Coverage Requirements

**✅ MANDATORY: Test naming and coverage patterns**
```java
// Class naming convention (MANDATORY)
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {  // Exact naming: {ClassName}Test
    
    @Test 
    void should_authenticate_user_when_valid_credentials_then_return_jwt() {
        // Test implementation
    }
    
    @Test
    void should_handle_circuit_open_when_platform_unavailable_then_fallback_to_cache() {
        // Circuit breaker failure test - MANDATORY for @CircuitBreaker classes
    }
}
```

**Coverage Requirements:**
- 100% line coverage for all service and controller classes
- Each service method must have: happy path, validation, exception, circuit breaker failure (if applicable)
- Integration tests: `{ClassName}IntegrationTest`
- Verify with: `mvn test jacoco:report`

---
project_name: 'spring_project'
user_name: 'Samuel'
date: '2026-04-25'
sections_completed: ['discovery', 'language_specific_rules', 'framework_specific_rules', 'testing_rules', 'code_quality_rules', 'workflow_rules', 'critical_anti_patterns']
status: 'complete'
total_patterns_documented: 47
critical_anti_patterns: 8
completion_date: '2026-04-25'
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

### Java/Spring Boot Language-Specific Rules

#### Multi-Tenant Context Management

**✅ REQUIRED: Use TenantContextHolder Pattern**
```java
@Component
public class TenantContextHolder {
    private static final ThreadLocal<String> tenantId = new ThreadLocal<>();
    
    public static void setTenant(String tenantId) { 
        TenantContextHolder.tenantId.set(tenantId); 
    }
    public static String getCurrentTenant() { 
        return tenantId.get(); 
    }
    public static void clear() { 
        tenantId.remove(); 
    }
}

// All service methods must use tenant context
@Service
public class RevenueService {
    public void processRevenue(RevenueData data) {
        String tenantId = TenantContextHolder.getCurrentTenant();
        // Process with tenant isolation
    }
}
```

**❌ FORBIDDEN: Direct queries without tenant context**
```java
// Missing tenant isolation - data leakage risk
revenueRepository.findByUserId(userId); // WRONG
```

#### Constructor Injection Enforcement

**✅ REQUIRED: Constructor injection only**
```java
@Service
public class AuthService {
    private final AuthenticationManager authManager;
    private final Clock clock;
    
    // Single constructor - Spring auto-wires
    public AuthService(AuthenticationManager authManager, Clock clock) {
        this.authManager = authManager;
        this.clock = clock;
    }
}
```

**❌ FORBIDDEN: Field injection**
```java
@Service  
public class AuthService {
    @Autowired private AuthenticationManager authManager; // WRONG
    @Autowired private Clock clock; // WRONG
}
```

#### JPA Entity Patterns

**✅ REQUIRED: Explicit naming and tenant-aware entities**
```java
@Entity
@Table(name = "creator_profiles")
public class CreatorProfile extends TenantAwareEntity {
    
    @Column(name = "display_name")
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "creator_category") 
    private CreatorCategory creatorCategory;
    
    @Column(name = "content_preferences", columnDefinition = "JSON")
    private String contentPreferences;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

@MappedSuperclass
public abstract class TenantAwareEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @PrePersist
    public void setTenantId() {
        if (tenantId == null) {
            tenantId = TenantContextHolder.getCurrentTenant();
        }
    }
}
```

#### Circuit Breaker Implementation Patterns

**✅ REQUIRED: Platform adapter circuit breaker pattern**
```java
@Component
@CircuitBreaker(name = "youtube-api", fallbackMethod = "fallbackToCache")
@RateLimiter(name = "youtube-api")
@Retry(name = "youtube-api")
public class YouTubeAdapter implements PlatformConnector {
    
    @Override
    public CompletableFuture<PlatformMetrics> getMetrics(String creatorId) {
        // Platform API call with automatic retry and circuit breaker
    }
    
    public PlatformMetrics fallbackToCache(String creatorId, Exception ex) {
        log.warn("YouTube API failure, falling back to cache: {}", ex.getMessage());
        return cachedMetricsService.getLastKnownMetrics(creatorId);
    }
    
    @Override
    public boolean isHealthy() {
        return circuitBreakerRegistry
            .circuitBreaker("youtube-api")
            .getState() == CircuitBreaker.State.CLOSED;
    }
}
```

#### Authentication Context Pattern

**✅ REQUIRED: Centralized authentication helper**
```java
@Component  
public class AuthenticationHelper {
    public String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
    
    public String getCurrentTenantId() {
        // Extract tenant from JWT claims or context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("tenant_id");
        }
        return TenantContextHolder.getCurrentTenant();
    }
}
```

**❌ FORBIDDEN: Direct SecurityContext access**
```java
// Don't do this in services/controllers
String userId = SecurityContextHolder.getContext().getAuthentication().getName(); // WRONG
```

#### Record DTO Validation Patterns

**✅ REQUIRED: Record DTOs with complete validation**
```java
public record CreateProfileRequest(
    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be 100 characters or less")
    String displayName,
    
    @NotNull(message = "Category is required")
    @Pattern(regexp = "GAMING|LIFESTYLE|BUSINESS|EDUCATION", 
             message = "Invalid creator category")
    String creatorCategory,
    
    @Valid @NotNull
    List<@Valid PlatformConnection> platforms
) {}
```

#### Test Coverage Requirements

**✅ MANDATORY: Test naming and coverage patterns**
```java
// Class naming convention (MANDATORY)
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {  // Exact naming: {ClassName}Test
    
    @Test 
    void should_authenticate_user_when_valid_credentials_then_return_jwt() {
        // Test implementation
    }
    
    @Test
    void should_handle_circuit_open_when_platform_unavailable_then_fallback_to_cache() {
        // Circuit breaker failure test - MANDATORY for @CircuitBreaker classes
    }
}
```

**Coverage Requirements:**
- 100% line coverage for all service and controller classes
- Each service method must have: happy path, validation, exception, circuit breaker failure (if applicable)
- Integration tests: `{ClassName}IntegrationTest`
- Verify with: `mvn test jacoco:report`

#### Compliance Audit Event Pattern

**✅ REQUIRED: Compliance audit logging**
```java
@Entity
@Table(name = "compliance_audit_events")
public class ComplianceAuditEvent extends TenantAwareEntity {
    
    @Enumerated(EnumType.STRING)
    private ComplianceEventType eventType; // FTC_DISCLOSURE, GDPR_ACCESS, etc.
    
    @Column(columnDefinition = "JSON")
    private String eventData; // Structured audit payload
    
    @Column(name = "user_action_required")
    private Boolean userActionRequired = false; // Human-in-the-loop flag
    
    @Column(name = "platform_context")
    private String platformContext; // YouTube, TikTok, etc.
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### Spring Boot Framework-Specific Rules

#### Multi-Profile Configuration Strategy

**✅ CHECKLIST: Before implementing configuration inheritance**
- [ ] Validate @ConfigurationProperties inheritance doesn't bypass validation
- [ ] Test with `--spring.profiles.active=dev,prod,tenant-specific`
- [ ] Verify child profiles override parent validation rules correctly
- [ ] Add integration test covering all inheritance levels

**✅ REQUIRED: Environment-specific configuration inheritance**
```yaml
# application.yml (base configuration)
spring:
  application:
    name: creator-platform
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
    include: 
      - actuator
      - security
      - cache

resilience4j:
  circuitbreaker:
    instances:
      platform-api:
        sliding-window-size: 10

---
# application-prod.yml  
spring:
  config:
    activate:
      on-profile: prod
      
resilience4j:
  circuitbreaker:
    instances:
      platform-api:
        failure-rate-threshold: 30
        wait-duration-in-open-state: 60s
        sliding-window-size: 20
```

**❌ DANGER: Configuration validation bypass**
```yaml
# Parent application.yml - validation gets lost here
spring:
  datasource:
    url: ${DB_URL:@null} # @null bypasses @NotNull validation
```

#### Security Configuration with Tenant Isolation

**🔒 SECURITY CHECKPOINT: Tenant-aware security filter chain**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/creator/**").hasAnyRole("CREATOR", "ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantContextFilter(), JwtAuthenticationFilter.class)
            .build();
    }
}

@Component
@Order(1)
public class TenantContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        try {
            String tenantId = extractTenantFromRequest((HttpServletRequest) request);
            TenantContextHolder.setTenant(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear(); // CRITICAL: Always clear
        }
    }
}
```

**⚠️ CRITICAL: Async execution safety**
```java
// ❌ DANGER: TenantContext lost in @Async methods
@Service
public class CreatorService {
    @Async
    public CompletableFuture<Creator> processCreator() {
        // TenantContextHolder.getCurrentTenant() returns null
    }
}

// ✅ CORRECT: Explicit tenant propagation
@Component
public class TenantTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        String tenantId = TenantContextHolder.getCurrentTenant();
        return () -> {
            TenantContextHolder.setTenant(tenantId);
            try { runnable.run(); }
            finally { TenantContextHolder.clear(); }
        };
    }
}
```

#### Hierarchical Cache Configuration

**✅ REQUIRED: Tenant-isolated caching strategy**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CompositeCacheManager composite = new CompositeCacheManager();
        composite.setCacheManagers(
            caffeineCacheManager(),
            redisCacheManager()
        );
        return composite;
    }
    
    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(5))
            .recordStats());
        return cacheManager;
    }
}
```

**🔍 CRITICAL: Cache key collision prevention**
```java
@Service
public class CreatorMetricsService {
    
    // ✅ REQUIRED: Tenant-prefixed cache keys
    @Cacheable(value = "creator-metrics", 
               key = "#tenantId + ':' + #creatorId", 
               condition = "#result != null")
    public CreatorMetrics getMetrics(String tenantId, String creatorId) {
        return platformApiClient.fetchMetrics(tenantId, creatorId);
    }
    
    // ❌ FORBIDDEN: Global cache keys (data leak risk)
    @Cacheable(value = "creator-metrics", key = "#creatorId") // WRONG
    public CreatorMetrics getMetrics(String creatorId) { }
}
```

**🧪 MANDATORY: Cache isolation test pattern**
```java
@Test
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
void should_prevent_cross_tenant_cache_collision() {
    // Tenant A data
    CreatorMetrics tenantAData = service.getMetrics("tenant-a", "creator-123");
    
    // Tenant B tries to access same creator ID
    CreatorMetrics tenantBData = service.getMetrics("tenant-b", "creator-123");
    
    assertThat(tenantBData).isNotEqualTo(tenantAData);
}
```

#### Production Actuator Configuration

**📊 PRE-DEPLOYMENT CHECKLIST:**
- [ ] Actuator endpoints secured behind authentication
- [ ] No tenant-specific data exposed in /health endpoint
- [ ] Custom health indicators test tenant isolation
- [ ] /metrics endpoint excludes sensitive tenant identifiers

**✅ REQUIRED: Production-safe actuator setup**
```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      group:
        readiness:
          include: db,redis,circuitBreakers
        liveness:
          include: diskSpace,ping
```

**🔒 REQUIRED: Tenant-safe health indicators**
```java
@Component
public class TenantSafeHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // ✅ SAFE: Aggregate health without tenant details
        return Health.up()
            .withDetail("total-tenants", tenantService.getActiveTenantCount())
            .build();
            
        // ❌ FORBIDDEN: Exposing tenant-specific data
        // .withDetail("tenant-ids", tenantService.getAllTenantIds()) // WRONG
    }
}
```

#### Database Migration Strategy with Flyway

**⚡ DEADLOCK PREVENTION PROTOCOL:**
- [ ] No active tenant operations during migration window
- [ ] Migration scripts tested with concurrent tenant access
- [ ] Rollback scripts prepared and tested

**✅ REQUIRED: Tenant-safe migration patterns**
```sql
-- V1.001__Create_tenant_aware_creator_profiles.sql
CREATE TABLE creator_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(36) NOT NULL,
    creator_id VARCHAR(36) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Tenant isolation
    INDEX idx_tenant_creator (tenant_id, creator_id),
    CONSTRAINT uk_tenant_creator UNIQUE (tenant_id, creator_id)
);

-- V1.002__Add_platform_metrics_partition.sql  
CREATE TABLE platform_metrics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(36) NOT NULL,
    creator_id VARCHAR(36) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    metric_date DATE NOT NULL,
    
    -- Partitioning for performance
    INDEX idx_tenant_creator_date (tenant_id, creator_id, metric_date)
) PARTITION BY RANGE (YEAR(metric_date)) (
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION p2027 VALUES LESS THAN (2028),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

**❌ FORBIDDEN: Large batch operations during migration**
```sql
-- Migration deadlock risk
INSERT INTO creator_profiles_new SELECT * FROM creator_profiles; -- WRONG
```

#### Spring Boot Testing Framework Patterns

**✅ REQUIRED: Test slice configuration with TestContainers**
```java
// Integration test with test containers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CreatorPlatformIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test_creator_platform")
        .withUsername("test")
        .withPassword("test")
        .withEnv("TZ", "UTC")
        .withCommand("--default-time-zone=+00:00");
        
    @Container 
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
        
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
}
```

**✅ MANDATORY: Framework-specific test patterns**
```java
// Repository slice test with tenant isolation
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreatorProfileRepositoryTest {
    
    @Test
    void should_isolate_data_by_tenant() {
        // Test tenant-aware repository queries
    }
}

// Web slice test with security context
@WebMvcTest(CreatorController.class)
class CreatorControllerTest {
    
    @Test
    @WithMockTenant("tenant-123")
    void should_return_creator_data_for_authenticated_tenant() {
        // Test controller with mocked tenant context
    }
}
```

### Testing Rules

#### Test Structure Organization

**✅ REQUIRED: Test framework setup**
```java
// Service unit tests
@ExtendWith(MockitoExtension.class)
class CreatorProfileServiceTest {  // Exact naming: {ClassName}Test
    
    @Mock private CreatorProfileRepository repository;
    @Mock private TenantContextHolder tenantContext;
    @InjectMocks private CreatorProfileService service;
    
    @BeforeEach
    void setUp() {
        // Setup with constructor injection pattern
    }
}

// Integration tests with TestContainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CreatorProfileIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withEnv("TZ", "UTC")
        .withCommand("--default-time-zone=+00:00");
}
```

#### Test Naming Conventions

**✅ MANDATORY: Consistent test naming**
- Service tests: `{ClassName}Test` (not `{ClassName}UnitTest`)
- Integration tests: `{ClassName}IntegrationTest`
- API contract tests: `{ClassName}ApiContractTest`

**✅ REQUIRED: Test method naming pattern**
```java
@Test
void should_{action}_when_{condition}_then_{expected_result}() {
    // Implementation
}

// Examples:
void should_create_profile_when_valid_request_then_return_profile_response()
void should_throw_exception_when_tenant_mismatch_then_return_forbidden()
void should_fallback_to_cache_when_circuit_open_then_return_cached_data()
```

#### Coverage Requirements

**🎯 MANDATORY: 100% line coverage for service and controller classes**
- Verification command: `mvn test jacoco:report`
- Each service method must have: happy path, validation, exception, circuit breaker failure (if applicable)
- Integration tests required for all API endpoints

**✅ REQUIRED: Test coverage per method**
```java
@Test  // 1. Happy path test
void should_authenticate_user_when_valid_credentials_then_return_jwt() {}

@Test  // 2. Input validation test  
void should_reject_authentication_when_invalid_email_then_throw_validation_exception() {}

@Test  // 3. Exception handling test
void should_handle_database_error_when_user_lookup_fails_then_throw_service_exception() {}

@Test  // 4. Circuit breaker failure test (for adapters)
void should_fallback_to_cache_when_platform_api_circuit_open_then_return_stale_data() {}
```

#### Multi-Tenant Testing Patterns

**🔒 CRITICAL: Tenant isolation verification**
```java
@Test
void should_isolate_data_by_tenant_when_different_tenants_access_same_resource() {
    // Arrange: Set up data for two different tenants
    TenantContextHolder.setTenant("tenant-a");
    CreatorProfile profileA = service.createProfile(request);
    TenantContextHolder.clear();
    
    TenantContextHolder.setTenant("tenant-b");
    // Act: Attempt to access tenant A's data from tenant B context
    // Assert: Should not return tenant A's data
    assertThatThrownBy(() -> service.getProfile(profileA.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
}

@Test
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
void should_prevent_cache_leakage_between_tenants() {
    // Test cache isolation between tenants
}
```

#### Circuit Breaker Testing Requirements

**⚡ MANDATORY: Circuit breaker state testing**
```java
@Test
void should_handle_circuit_breaker_transitions() {
    // Test CLOSED → OPEN transition
    // Simulate failures to trigger circuit opening
    
    // Test OPEN → HALF_OPEN transition
    // Wait for circuit breaker timeout
    
    // Test HALF_OPEN → CLOSED transition
    // Successful calls should close circuit
}

@Test
void should_execute_fallback_when_circuit_open() {
    // Force circuit breaker to OPEN state
    // Verify fallback method execution
    // Verify cache access for stale data
}
```

#### Security Testing Patterns

**🔐 REQUIRED: Authentication and authorization testing**
```java
// Custom annotation for tenant context in tests
@WithMockTenant("tenant-123")
@Test
void should_access_creator_data_when_authenticated_as_tenant() {
    // Test with mocked tenant context
}

@Test
@WithMockUser(roles = {"CREATOR"})
void should_allow_creator_operations_when_creator_role() {
    // Test role-based access control
}

@Test
void should_propagate_tenant_context_in_async_operations() {
    // Test async tenant context propagation
    CompletableFuture<String> result = service.processAsync();
    assertThat(result.get()).contains("tenant-123");
}
```

#### Spring Boot Test Slice Patterns

**✅ REQUIRED: Appropriate test slices**
```java
// Repository layer testing
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreatorProfileRepositoryTest {
    @Test
    void should_find_by_tenant_and_creator_id() {
        // Test tenant-aware repository methods
    }
}

// Web layer testing
@WebMvcTest(CreatorProfileController.class)
class CreatorProfileControllerTest {
    @MockBean private CreatorProfileService service;
    
    @Test
    void should_return_profile_when_valid_request() throws Exception {
        mockMvc.perform(get("/api/v1/profile")
            .header("Authorization", "Bearer " + validJwt))
            .andExpected(status().isOk());
    }
}

// Service layer testing (full Spring context)
@SpringBootTest
@Transactional
class CreatorProfileServiceIntegrationTest {
    @Test
    void should_integrate_with_real_database_and_cache() {
        // Test service with real dependencies
    }
}
```

### Code Quality & Style Rules

#### Lombok Usage Patterns

**✅ REQUIRED: Selective Lombok usage**
```java
// Entity classes - explicit control over serialization
@Entity
@Table(name = "creator_profiles")
public class CreatorProfile extends TenantAwareEntity {
    // Manual getters/setters (no @Data)
    // Explicit constructors for JPA requirements
}

// Service classes - constructor injection pattern
@Service
@Slf4j  // Logging only
public class CreatorProfileService {
    private final CreatorProfileRepository repository;
    
    // Manual constructor (no @RequiredArgsConstructor)
    public CreatorProfileService(CreatorProfileRepository repository) {
        this.repository = repository;
    }
}

// DTOs - prefer record pattern
public record CreateProfileRequest(
    @NotBlank String displayName,
    @NotNull String creatorCategory
) {}
```

**❌ FORBIDDEN: Excessive Lombok in critical classes**
```java
@Entity
@Data  // WRONG - breaks encapsulation in entities
@AllArgsConstructor  // WRONG - breaks JPA constructor requirements
public class CreatorProfile {}
```

#### Code Organization Patterns

**✅ REQUIRED: Package visibility control**
```java
// Public interfaces for cross-domain communication
public interface PlatformConnector {
    CompletableFuture<PlatformMetrics> getMetrics(String creatorId);
}

// Package-private implementation classes
@Component
class YouTubePlatformConnector implements PlatformConnector {
    // Implementation details hidden
}

// Utility classes in shared package
public final class TenantUtils {
    private TenantUtils() {} // Prevent instantiation
    
    public static String formatTenantKey(String tenantId, String resource) {
        return tenantId + ":" + resource;
    }
}
```

#### Exception Handling Patterns

**✅ REQUIRED: Custom exception hierarchy**
```java
// Base runtime exception for application
public abstract class CreatorPlatformException extends RuntimeException {
    protected CreatorPlatformException(String message) {
        super(message);
    }
    
    protected CreatorPlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Specific business exceptions
public class ProfileAlreadyExistsException extends CreatorPlatformException {
    public ProfileAlreadyExistsException(String userId) {
        super("Creator profile already exists for user: " + userId);
    }
}

public class ResourceNotFoundException extends CreatorPlatformException {
    public ResourceNotFoundException(String resource, String id) {
        super(String.format("%s not found with id: %s", resource, id));
    }
}
```

**✅ REQUIRED: Global exception handler**
```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ProfileAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileExists(ProfileAlreadyExistsException ex) {
        log.warn("Profile creation conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.info("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }
}
```

#### Logging Patterns

**✅ REQUIRED: Structured logging with security**
```java
@Service
@Slf4j
public class CreatorProfileService {
    
    public CreatorProfileResponse createProfile(String userId, CreateProfileRequest request) {
        // ✅ SAFE: Log business events without sensitive data
        log.info("Creating profile for creator category: {}", request.creatorCategory());
        
        // ✅ SAFE: Log with tenant context (obfuscated)
        String tenantHash = DigestUtils.md5Hex(tenantId).substring(0, 8);
        log.debug("Profile creation in tenant: {}", tenantHash);
        
        try {
            // Business logic
            log.info("Profile created successfully");
            return response;
        } catch (Exception ex) {
            // ✅ REQUIRED: Error logging with context
            log.error("Failed to create profile for category: {}", 
                request.creatorCategory(), ex);
            throw ex;
        }
    }
}
```

**❌ FORBIDDEN: Sensitive data in logs**
```java
// WRONG - exposes sensitive information
log.info("User email: {}", user.getEmail()); // SECURITY RISK
log.debug("Tenant ID: {}", tenantId); // PRIVACY VIOLATION
log.error("Database password: {}", password); // CREDENTIAL EXPOSURE
```

#### JavaDoc Requirements

**✅ REQUIRED: Documentation for public APIs**
```java
/**
 * Creates a new creator profile for the authenticated user.
 * 
 * <p>This operation is tenant-aware and will create the profile
 * within the current tenant context. Each user can have only
 * one profile per tenant.
 * 
 * @param userId the unique identifier of the user
 * @param request the profile creation request with validation
 * @return the created profile response with generated ID
 * @throws ProfileAlreadyExistsException if user already has a profile
 * @throws TenantContextMissingException if no tenant context is set
 */
public CreatorProfileResponse createProfile(String userId, CreateProfileRequest request) {
    // Implementation
}

/**
 * Repository for managing tenant-aware creator profiles.
 * 
 * <p>All queries automatically include tenant isolation through
 * the {@code TenantAwareEntity} base class. Manual tenant filtering
 * is not required but can be added for performance optimization.
 */
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {
    
    /**
     * Finds a creator profile by user ID within the current tenant context.
     * 
     * @param userId the user identifier
     * @return the profile if found within the current tenant
     */
    Optional<CreatorProfile> findByUserId(String userId);
}
```

#### Variable and Method Naming Conventions

**✅ REQUIRED: Consistent naming patterns**
```java
// Class names - PascalCase
public class CreatorProfileService {}
public enum CreatorCategory { GAMING, LIFESTYLE, TECH }

// Method names - camelCase with verb prefix
public CreatorProfile createProfile() {}
public Optional<CreatorProfile> findByUserId() {}
public boolean isProfileActive() {}
public void validateProfileData() {}

// Variable names - camelCase, descriptive
String tenantId;
CreatorProfileRequest profileRequest;
List<PlatformConnection> platformConnections;

// Constants - UPPER_SNAKE_CASE
public static final String DEFAULT_CREATOR_CATEGORY = "OTHER";
public static final Duration CACHE_TTL = Duration.ofMinutes(30);

// Database columns - snake_case (in @Column annotations)
@Column(name = "display_name")
private String displayName;

@Column(name = "created_at")
private LocalDateTime createdAt;
```

#### Import Organization

**✅ REQUIRED: Clean import structure**
```java
// Java standard library
import java.time.LocalDateTime;
import java.util.Optional;

// Third-party libraries (alphabetical by group)
import jakarta.persistence.*;
import jakarta.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;

// Application imports (alphabetical)
import com.samuel.app.creator.dto.CreateProfileRequest;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.shared.security.TenantContextHolder;
```

**❌ FORBIDDEN: Wildcard imports and static import abuse**
```java
import java.util.*; // WRONG - use specific imports
import static org.mockito.Mockito.*; // WRONG - be explicit about static methods
```

#### Null Handling and Optional Usage

**✅ REQUIRED: Optional for potentially missing values**
```java
// Repository methods returning Optional
public Optional<CreatorProfile> findByUserId(String userId) {
    return repository.findByUserId(userId);
}

// Service methods handling Optional gracefully  
public CreatorProfileResponse getProfile(String userId) {
    return repository.findByUserId(userId)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));
}

// Method parameters - prefer non-null with validation
public void updateProfile(@Valid @NotNull UpdateProfileRequest request) {
    Objects.requireNonNull(request, "Profile request cannot be null");
    // Implementation
}
```

**✅ REQUIRED: Null checks for external data**
```java
// Platform API responses may be null
@Override
public PlatformMetrics fetchMetrics(String creatorId) {
    ApiResponse response = platformClient.getMetrics(creatorId);
    
    if (response == null || response.getData() == null) {
        log.warn("Null response from platform API for creator: {}", creatorId);
        return PlatformMetrics.empty();
    }
    
    return mapToMetrics(response.getData());
}
```

### Development Workflow Rules

#### BMad Story Development Cycle

**✅ REQUIRED: Story-driven development process**
```bash
# 1. Create story from sprint plan
# Use BMad: [CS] Create Story or [CS] Create Story {story-identifier}
# Story file created in: _bmad-output/implementation-artifacts/{story-id}.md

# 2. Create feature branch with story ID
git checkout -b feature/1-5-basic-creator-workspace-dashboard

# 3. Implement following story specifications
# Use BMad: [DS] Dev Story with story file context

# 4. Create PR linking to story file
# 5. Code review using BMad: [CR] Code Review
# 6. Merge after approval and tests pass
```

**✅ REQUIRED: Story file structure reference**
- Story location: `_bmad-output/implementation-artifacts/{epic}-{story}-{brief-description}.md`
- Sprint status tracking: `_bmad-output/implementation-artifacts/sprint-status.yaml`
- Implementation artifacts must reference specific story requirements

#### Git Branch Strategy

**✅ REQUIRED: Branch naming conventions**
```bash
# Feature branches for story development
feature/{epic-number}-{story-number}-{brief-description}
# Example: feature/1-5-basic-creator-workspace-dashboard

# Integration branches for epic testing  
integration/epic-{number}
# Example: integration/epic-1

# Release branches
release/v{major}.{minor}.{patch}
# Example: release/v1.0.0

# Hotfix branches
hotfix/{issue-description}
# Example: hotfix/tenant-context-leak
```

#### Commit Message Patterns

**✅ REQUIRED: Structured commit messages**
```bash
# Story implementation commits
[{story-id}] {action}: {brief description}
# Examples:
[1-5] feat: add basic creator workspace dashboard controller
[1-5] test: add dashboard integration tests with tenant isolation
[1-5] fix: resolve cache key collision in dashboard metrics

# Bug fixes
fix: {description of fix}
# Example: fix: prevent cross-tenant data leakage in cache keys

# Infrastructure and tooling
chore: {infrastructure change}
# Example: chore: update TestContainer MySQL to 8.0.35

# Documentation
docs: {documentation change}
# Example: docs: update API documentation for creator endpoints
```

#### Pull Request Requirements

**📋 MANDATORY: PR checklist before merge**
- [ ] Links to story file in `_bmad-output/implementation-artifacts/`
- [ ] 100% test coverage maintained (verify with `mvn test jacoco:report`)
- [ ] All `@CircuitBreaker` classes have failure scenario tests
- [ ] Tenant isolation verified in integration tests
- [ ] No sensitive data exposed in logs or API responses
- [ ] Database migrations tested with existing data
- [ ] Actuator endpoints secured and tested

**✅ REQUIRED: PR description template**
```markdown
## Story Reference
- Story File: `_bmad-output/implementation-artifacts/{story-id}.md`
- Epic: {epic-number} - {epic-name}
- Sprint Status: [link to sprint-status.yaml]

## Changes Made
- [ ] {specific change 1}
- [ ] {specific change 2}

## Testing
- [ ] Unit tests: {coverage percentage}%
- [ ] Integration tests: {test scenarios covered}
- [ ] Manual testing: {scenarios verified}

## Multi-Tenant Verification
- [ ] Tenant context isolation tested
- [ ] Cross-tenant data access prevented
- [ ] Cache keys include tenant prefix
```

#### Deployment Patterns

**✅ REQUIRED: Environment-specific deployment**
```yaml
# Environment progression
dev → integration → staging → production

# Configuration profiles per environment
spring.profiles.active: dev,actuator,security
spring.profiles.active: staging,actuator,security,monitoring  
spring.profiles.active: prod,actuator,security,monitoring,audit
```

**🐳 REQUIRED: Docker deployment configuration**
```dockerfile
# Multi-stage build for production
FROM openjdk:17-jdk AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src  
RUN mvn clean package

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# JVM configuration for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0"

# Health check configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

#### CI/CD Pipeline Requirements

**✅ MANDATORY: Pipeline stages and gates**
```yaml
# 1. Build and Test Stage
- Maven clean compile
- Unit tests with JaCoCo coverage (100% for services/controllers)
- Integration tests with TestContainers
- Security vulnerability scanning

# 2. Quality Gate Stage  
- SonarQube code quality analysis
- Tenant isolation verification tests
- Circuit breaker failure scenario tests
- API contract testing

# 3. Deployment Stage (per environment)
- Database migration execution (Flyway)
- Application deployment with health checks
- Actuator endpoint validation
- Multi-tenant smoke tests

# 4. Post-Deployment Verification
- Health check validation (/actuator/health)
- Circuit breaker metrics monitoring
- Cache isolation verification
- Compliance audit log validation
```

**🔐 REQUIRED: Security pipeline checks**
```bash
# Security scanning steps
mvn dependency-check:check  # OWASP dependency vulnerabilities
mvn spotbugs:check         # Static code analysis
mvn pmd:check              # Code quality rules

# Container security scanning
docker scan creator-platform:latest

# Secrets scanning
git-secrets --scan
truffleHog --regex --entropy=False .
```

#### Release Management

**✅ REQUIRED: Release preparation checklist**
- [ ] All epic stories completed and tested
- [ ] Integration tests pass with production-like data volumes
- [ ] Database migrations tested with rollback procedures
- [ ] Circuit breaker thresholds validated under load
- [ ] Multi-tenant isolation verified under concurrent access
- [ ] Compliance audit trails tested end-to-end
- [ ] Performance benchmarks meet requirements
- [ ] Rollback procedure documented and tested

**🚨 REQUIRED: Hotfix procedures**
```bash
# 1. Create hotfix branch from production
git checkout main
git pull origin main
git checkout -b hotfix/critical-tenant-leak-fix

# 2. Implement minimal fix with tests
# 3. Fast-track code review (same-day approval required)
# 4. Deploy to staging for verification
# 5. Deploy to production with monitoring
# 6. Monitor for 24 hours post-deployment
# 7. Merge back to main and develop branches
```

### Critical Don't-Miss Rules

#### 🚨 ANTI-PATTERN: Multi-Tenant Data Leakage

**❌ NEVER: Query without tenant context**
```java
// DANGEROUS - Cross-tenant data exposure
@Repository
public class CreatorProfileRepository {
    public List<CreatorProfile> findByCategory(CreatorCategory category) {
        return entityManager.createQuery(
            "SELECT c FROM CreatorProfile c WHERE c.creatorCategory = :category", 
            CreatorProfile.class)
            .setParameter("category", category)
            .getResultList(); // WRONG - Returns data from ALL tenants
    }
}
```

**✅ ALWAYS: Include tenant isolation**
```java
// SAFE - Tenant-isolated queries
@Repository
public class CreatorProfileRepository {
    public List<CreatorProfile> findByCategory(CreatorCategory category) {
        String tenantId = TenantContextHolder.getCurrentTenant();
        if (tenantId == null) {
            throw new TenantContextMissingException("Tenant context required for data access");
        }
        
        return entityManager.createQuery(
            "SELECT c FROM CreatorProfile c WHERE c.tenantId = :tenantId AND c.creatorCategory = :category", 
            CreatorProfile.class)
            .setParameter("tenantId", tenantId)
            .setParameter("category", category)
            .getResultList();
    }
}
```

#### 🚨 ANTI-PATTERN: Cache Key Collisions

**❌ NEVER: Global cache keys in multi-tenant system**
```java
// DANGEROUS - Tenants can access each other's cached data
@Service
public class CreatorMetricsService {
    @Cacheable(value = "creator-metrics", key = "#creatorId")
    public CreatorMetrics getMetrics(String creatorId) {
        // Cache key "creator-123" is shared across ALL tenants
        return platformClient.fetchMetrics(creatorId);
    }
}
```

**✅ ALWAYS: Tenant-prefixed cache keys**
```java
// SAFE - Tenant isolation in cache
@Service
public class CreatorMetricsService {
    @Cacheable(value = "creator-metrics", 
               key = "T(com.samuel.app.shared.security.TenantContextHolder).getCurrentTenant() + ':' + #creatorId")
    public CreatorMetrics getMetrics(String creatorId) {
        // Cache key becomes "tenant-abc:creator-123"
        return platformClient.fetchMetrics(creatorId);
    }
}
```

#### 🚨 ANTI-PATTERN: Async Tenant Context Loss

**❌ NEVER: Direct @Async with tenant operations**
```java
// DANGEROUS - Loses tenant context in async execution
@Service
public class ContentPublishingService {
    
    @Async
    public CompletableFuture<PublishResult> publishToAllPlatforms(String contentId) {
        // TenantContextHolder.getCurrentTenant() returns null here!
        String tenantId = TenantContextHolder.getCurrentTenant(); // null
        return CompletableFuture.completedFuture(result);
    }
}
```

**✅ ALWAYS: Explicit tenant context propagation**
```java
// SAFE - Explicit tenant context handling
@Service
public class ContentPublishingService {
    
    @Async
    public CompletableFuture<PublishResult> publishToAllPlatforms(String tenantId, String contentId) {
        return CompletableFuture.supplyAsync(() -> {
            TenantContextHolder.setTenant(tenantId);
            try {
                return platformService.publishContent(contentId);
            } finally {
                TenantContextHolder.clear();
            }
        });
    }
    
    // Caller method passes tenant explicitly
    public void initiatePublishing(String contentId) {
        String tenantId = TenantContextHolder.getCurrentTenant();
        publishToAllPlatforms(tenantId, contentId);
    }
}
```

#### 🚨 ANTI-PATTERN: Circuit Breaker Implementation Failures

**❌ NEVER: Circuit breaker without fallback**
```java
// DANGEROUS - No graceful degradation
@Component
public class YouTubeAdapter {
    @CircuitBreaker(name = "youtube-api") // Missing fallbackMethod
    public CreatorMetrics fetchMetrics(String creatorId) {
        return youtubeApi.getMetrics(creatorId); // Fails completely when circuit opens
    }
}
```

**✅ ALWAYS: Implement fallback and health checks**
```java
// SAFE - Complete circuit breaker implementation
@Component
public class YouTubeAdapter {
    
    @CircuitBreaker(name = "youtube-api", fallbackMethod = "fallbackToCache")
    @RateLimiter(name = "youtube-api")
    @Retry(name = "youtube-api")
    public CreatorMetrics fetchMetrics(String creatorId) {
        return youtubeApi.getMetrics(creatorId);
    }
    
    // REQUIRED: Fallback method with same signature + Exception parameter
    public CreatorMetrics fallbackToCache(String creatorId, Exception ex) {
        log.warn("YouTube API circuit breaker triggered, using cached data: {}", ex.getMessage());
        return cacheService.getLastKnownMetrics(creatorId)
            .orElse(CreatorMetrics.empty());
    }
    
    // REQUIRED: Health check method
    public boolean isHealthy() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("youtube-api");
        return cb.getState() == CircuitBreaker.State.CLOSED;
    }
}
```

#### 🚨 ANTI-PATTERN: Security Context Direct Access

**❌ NEVER: Direct SecurityContextHolder in business logic**
```java
// DANGEROUS - Scattered security context access
@RestController
public class CreatorController {
    
    @PostMapping("/profile")
    public ResponseEntity<CreatorProfile> createProfile(@RequestBody CreateProfileRequest request) {
        // WRONG - Direct access scattered throughout codebase
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        String tenantId = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication())
            .getToken().getClaimAsString("tenant_id");
            
        return creatorService.createProfile(userId, tenantId, request);
    }
}
```

**✅ ALWAYS: Centralized authentication helper**
```java
// SAFE - Centralized security context management
@RestController
public class CreatorController {
    
    private final AuthenticationHelper authHelper;
    
    @PostMapping("/profile") 
    public ResponseEntity<CreatorProfile> createProfile(@RequestBody CreateProfileRequest request) {
        // CORRECT - Centralized access pattern
        String userId = authHelper.getCurrentUserId();
        String tenantId = authHelper.getCurrentTenantId();
        
        return creatorService.createProfile(userId, tenantId, request);
    }
}
```

#### 🚨 ANTI-PATTERN: Database Migration Deadlocks

**❌ NEVER: Large batch operations during migration**
```sql
-- DANGEROUS - Causes table locks and deadlocks
BEGIN;

-- WRONG - Locks entire table during migration
INSERT INTO creator_profiles_new 
SELECT * FROM creator_profiles; -- Processes millions of rows at once

-- WRONG - Creates long-running transaction
ALTER TABLE creator_profiles ADD COLUMN new_field VARCHAR(255);
UPDATE creator_profiles SET new_field = 'default_value'; -- Updates all rows

COMMIT;
```

**✅ ALWAYS: Small batch migrations with proper locking**
```sql
-- SAFE - Incremental migration approach
BEGIN;

-- CORRECT - Small batch operations
CREATE TABLE creator_profiles_new (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(36) NOT NULL,
    -- Other columns
    INDEX idx_tenant_creator (tenant_id, creator_id)
);

COMMIT;

-- Separate transactions for data migration (handled by application code)
-- Batch size: 1000 records per transaction
-- Migration coordination through @Lock annotation in service layer
```

#### 🚨 ANTI-PATTERN: Actuator Security Exposure

**❌ NEVER: Expose sensitive data in health checks**
```java
// DANGEROUS - Leaks tenant and user information
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        return Health.up()
            .withDetail("active-tenants", tenantService.getAllTenantIds()) // BREACH
            .withDetail("user-count", userService.getTotalUserCount()) // BREACH
            .withDetail("recent-logins", authService.getRecentLoginEmails()) // MAJOR BREACH
            .build();
    }
}
```

**✅ ALWAYS: Sanitized health information**
```java
// SAFE - Aggregate metrics without sensitive data
@Component  
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            boolean canConnect = testDatabaseConnection();
            if (!canConnect) {
                return Health.down()
                    .withDetail("error", "Database connection failed")
                    .build();
            }
            
            return Health.up()
                .withDetail("connection-pool", getPoolStatus()) // Safe: pool metrics
                .withDetail("tenant-count", getTenantCountRange()) // Safe: ranges only
                .build();
                
        } catch (Exception ex) {
            return Health.down()
                .withDetail("error", "Health check failed")
                .withException(ex)
                .build();
        }
    }
    
    private String getTenantCountRange() {
        int count = tenantService.getActiveTenantCount();
        if (count < 10) return "1-10";
        if (count < 100) return "10-100"; 
        if (count < 1000) return "100-1000";
        return "1000+";
    }
}
```

#### 🚨 ANTI-PATTERN: Test Coverage Gaps

**❌ NEVER: Skip circuit breaker failure testing**
```java
// DANGEROUS - Missing critical failure scenario tests
@Test
void should_fetch_creator_metrics() {
    // WRONG - Only tests happy path
    CreatorMetrics metrics = youtubeAdapter.fetchMetrics("creator-123");
    assertThat(metrics).isNotNull();
    // Missing: What happens when circuit breaker opens?
    // Missing: What happens when rate limit exceeded?
    // Missing: What happens when fallback fails?
}
```

**✅ ALWAYS: Test all circuit breaker states**
```java
// SAFE - Complete circuit breaker test coverage
@Test
void should_handle_circuit_breaker_state_transitions() {
    // Test CLOSED → OPEN transition
    simulateApiFailures(10); // Trigger circuit opening
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    
    // Test fallback execution when circuit is OPEN
    CreatorMetrics metrics = youtubeAdapter.fetchMetrics("creator-123");
    assertThat(metrics).isEqualTo(cachedFallbackData);
    
    // Test OPEN → HALF_OPEN transition
    awaitCircuitBreakerTimeout();
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    
    // Test HALF_OPEN → CLOSED transition
    simulateApiSuccess();
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
}

@Test
void should_prevent_cross_tenant_fallback_data_leakage() {
    // Simulate circuit breaker open for tenant A
    TenantContextHolder.setTenant("tenant-a");
    simulateApiFailures(10);
    
    // Verify tenant B doesn't get tenant A's cached data
    TenantContextHolder.setTenant("tenant-b"); 
    CreatorMetrics metrics = youtubeAdapter.fetchMetrics("creator-123");
    assertThat(metrics.getTenantId()).isEqualTo("tenant-b");
}
```

#### 🚨 ANTI-PATTERN: Configuration Validation Bypass

**❌ NEVER: Skip validation in configuration inheritance**
```java
// DANGEROUS - Validation bypassed in profile inheritance
@ConfigurationProperties("app.platform")
public class PlatformConfig extends BaseConfig {
    @NotBlank // This validation is ignored during profile inheritance!
    private String apiKey;
    
    @Valid // This validation doesn't trigger properly!
    private CircuitBreakerConfig circuitBreaker;
}
```

**✅ ALWAYS: Explicit validation with @PostConstruct**
```java
// SAFE - Explicit validation enforcement
@ConfigurationProperties("app.platform")
@Validated
public class PlatformConfig extends BaseConfig {
    
    @NotBlank(message = "Platform API key is required")
    private String apiKey;
    
    @Valid
    @NotNull(message = "Circuit breaker configuration is required")
    private CircuitBreakerConfig circuitBreaker;
    
    @PostConstruct
    public void validateConfiguration() {
        if (circuitBreaker.getFailureRateThreshold() < 1 || circuitBreaker.getFailureRateThreshold() > 100) {
            throw new IllegalArgumentException("Circuit breaker failure rate must be between 1-100%");
        }
        
        if (apiKey != null && apiKey.startsWith("test-") && !"dev".equals(activeProfile)) {
            throw new IllegalArgumentException("Test API key cannot be used in non-dev environments");
        }
    }
}
```

---

## Summary

This project context document provides comprehensive implementation rules for AI agents working on the **spring_project** creator economy SaaS platform. The rules cover:

- **Multi-tenant architecture** with strict data isolation
- **Circuit breaker resilience** for platform API integrations  
- **Spring Boot security** with tenant-aware authentication
- **Comprehensive testing** with 100% coverage requirements
- **Code quality standards** with security-first patterns
- **Development workflows** integrated with BMad methodology
- **Critical anti-patterns** to prevent security and data leakage issues

All patterns are designed to maintain platform-agnostic core functionality while ensuring FTC/GDPR compliance through human-supervised audit trails.
```