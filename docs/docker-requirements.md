# Docker Requirements & Setup Guide

**Project:** spring_project (Creator Platform)  
**Last Updated:** 2026-04-26

## Prerequisites

### Required Software Versions

| Software | Minimum Version | Recommended | Notes |
|----------|----------------|-------------|-------|
| Docker | 20.10+ | 24.0+ | For Apple Silicon, use Docker Desktop 4.0+ |
| Docker Compose | 2.0+ | 2.20+ | Bundled with Docker Desktop |
| Maven | 3.8+ | 3.9.6 | Used for dependency management |
| Java (for local dev) | 17 | 17 LTS | OpenJDK or Oracle JDK |
| Git | 2.30+ | Latest | Version control |

### System Requirements

- **CPU:** 4+ cores recommended
- **RAM:** 8GB minimum, 16GB recommended
- **Disk:** 20GB free space for images and volumes
- **OS:** macOS, Linux, or Windows 10/11 with WSL2

---

## Technology Stack

### Application Runtime
- **Java:** 17 (OpenJDK)
- **Spring Boot:** 3.3.5
- **Maven:** 3.9.6
- **Build Tool:** Maven Wrapper (included in project)

### Infrastructure Services
- **MySQL:** 8.0 (database)
- **Redis:** 7.2-alpine (caching)
- **MailHog:** v1.0.1 (development email testing)

---

## Compatibility Matrix

### Verified Component Versions

```
Java 17 (OpenJDK)
  ├─ Spring Boot 3.3.5 ✓
  ├─ Spring Framework 6.1.14 ✓
  ├─ MySQL Connector J 8.3.0 ✓
  ├─ Flyway 10.10.0 ✓
  └─ Hibernate 6.5.3.Final ✓

MySQL 8.0
  ├─ MySQL Connector J 8.3.0 ✓
  ├─ Flyway MySQL 10.10.0 ✓
  └─ HikariCP 5.1.0 ✓

Redis 7.2-alpine
  ├─ Spring Data Redis 3.3.5 ✓
  ├─ Lettuce Client (managed) ✓
  └─ Spring Cache Abstraction ✓
```

---

## Environment Setup

### 1. Clone Repository
```bash
git clone <repository-url>
cd spring_project
```

### 2. Create Environment File
Create a `.env` file in the project root:

```bash
# Database Configuration
DB_URL=jdbc:mysql://mysql:3306/spring_project
DB_USERNAME=root
DB_PASSWORD=secure_password_here

# JWT Configuration
JWT_SECRET=your-256-bit-secret-key-here-change-in-production

# Optional: Override default ports
# MYSQL_PORT=3306
# REDIS_PORT=6379
# APP_PORT=8080
```

**Security Note:** Never commit `.env` file to version control. Use `.env.example` as a template.

### 3. Start Services

**Development Mode (with hot reload):**
```bash
docker-compose up
```

**Production Mode:**
```bash
# Edit docker-compose.yml to use Dockerfile instead of Dockerfile.dev
docker-compose up -d
```

**First Time Setup:**
```bash
# Build images and start services
docker-compose up --build

# Verify all services are healthy
docker-compose ps

# Check application health
curl http://localhost:8080/api/v1/actuator/health
```

### 4. Verify Installation

**Check Service Status:**
```bash
docker-compose ps
```

Expected output:
```
NAME                        STATUS              PORTS
authentication-app          Up (healthy)        0.0.0.0:8080->8080/tcp, 5005->5005/tcp
creator-platform-mysql      Up (healthy)        0.0.0.0:3306->3306/tcp
authentication-redis        Up                  0.0.0.0:6379->6379/tcp
authentication-mail         Up                  0.0.0.0:1025->1025/tcp, 8025->8025/tcp
```

**Access Points:**
- Application API: http://localhost:8080/api/v1
- Health Check: http://localhost:8080/api/v1/actuator/health
- MailHog Web UI: http://localhost:8025
- MySQL: localhost:3306
- Redis: localhost:6379

---

## Docker Configuration Details

### Service Architecture

```
┌─────────────────────────────────────────────────┐
│  app-network (bridge)                           │
│                                                 │
│  ┌───────────────────┐                         │
│  │  authentication-  │  Port 8080, 5005        │
│  │  app              ├────────────────────┐    │
│  │  (Spring Boot)    │                    │    │
│  └─────┬─────────────┘                    │    │
│        │                                   │    │
│        ├──────────┬──────────┬────────────┘    │
│        │          │          │                 │
│  ┌─────▼──────┐  │  ┌───────▼───┐  ┌─────────▼─┐
│  │ MySQL 8.0  │  │  │ Redis 7.2 │  │ MailHog   │
│  │ Port 3306  │  │  │ Port 6379 │  │ Ports     │
│  │            │  │  │           │  │ 1025,8025 │
│  └────────────┘  │  └───────────┘  └───────────┘
│                  │                               │
└──────────────────┼───────────────────────────────┘
                   │
            [Persisted Volumes]
            - mysql_data (database)
            - uploads (user files)
            - logs (application logs)
```

### Container Resources

| Service | CPU Limit | Memory Limit | CPU Reserve | Memory Reserve |
|---------|-----------|--------------|-------------|----------------|
| app | 2 cores | 2GB | 0.5 cores | 1GB |
| mysql | 1 core | 1GB | 0.25 cores | 512MB |
| redis | 0.5 cores | 256MB | 0.1 cores | 128MB |
| mailhog | 0.5 cores | 256MB | 0.1 cores | 128MB |

**Total Requirements:**
- CPU: 4 cores (limits), 0.85 cores (reserved)
- Memory: 3.5GB (limits), 1.75GB (reserved)

### Volume Mounts

| Path (Host) | Path (Container) | Purpose | Type |
|-------------|------------------|---------|------|
| mysql_data | /var/lib/mysql | Database persistence | Named Volume |
| ./uploads | /app/uploads | User-uploaded files | Bind Mount |
| ./logs | /app/logs | Application logs | Bind Mount |
| ./tmp | /tmp | Temporary files | Bind Mount |
| ./ | /app | Live code reload (dev only) | Bind Mount |
| ~/.m2 | /root/.m2 | Maven cache (dev only) | Bind Mount |

---

## Development Workflow

### Hot Reload Development
The `Dockerfile.dev` configuration supports live code reload:

```bash
# Start in development mode
docker-compose up

# Make code changes
# Spring Boot DevTools will automatically reload
# Check logs: docker-compose logs -f app
```

### Debugging
Remote debugging is enabled on port 5005:

**IntelliJ IDEA:**
1. Run > Edit Configurations > Add New > Remote JVM Debug
2. Host: localhost, Port: 5005
3. Start docker-compose
4. Start debugger

**VS Code:**
```json
{
  "type": "java",
  "name": "Attach to Docker",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

### Database Management

**Access MySQL:**
```bash
docker exec -it creator-platform-mysql mysql -u root -p
# Enter password from .env file
```

**Run Migrations:**
```bash
# Migrations run automatically on startup via Flyway
# Check migration status:
docker-compose exec app ./mvnw flyway:info
```

**Backup Database:**
```bash
docker exec creator-platform-mysql mysqldump -u root -p spring_project > backup.sql
```

**Restore Database:**
```bash
docker exec -i creator-platform-mysql mysql -u root -p spring_project < backup.sql
```

### Redis Commands

**Access Redis CLI:**
```bash
docker exec -it authentication-redis redis-cli
```

**Common Commands:**
```redis
# Check connection
PING

# View all keys
KEYS *

# Clear cache
FLUSHDB

# Monitor real-time commands
MONITOR
```

### Email Testing with MailHog

**Web Interface:** http://localhost:8025

**Features:**
- View all sent emails
- Test email formatting
- No actual email delivery
- Perfect for development testing

---

## Common Operations

### Start Services
```bash
# Foreground (see logs)
docker-compose up

# Background (detached)
docker-compose up -d

# Rebuild images
docker-compose up --build
```

### Stop Services
```bash
# Graceful stop
docker-compose down

# Stop and remove volumes (WARNING: deletes data)
docker-compose down -v
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f app
docker-compose logs -f mysql

# Last 100 lines
docker-compose logs --tail=100
```

### Restart Service
```bash
# Restart single service
docker-compose restart app

# Restart all services
docker-compose restart
```

### Execute Commands in Container
```bash
# Access shell
docker-compose exec app /bin/bash

# Run Maven commands
docker-compose exec app ./mvnw clean test

# Check Java version
docker-compose exec app java -version
```

---

## Troubleshooting

### Port Already in Use
```bash
# Find process using port
lsof -ti:8080

# Kill process
kill -9 <PID>

# Or change port in docker-compose.yml
```

### Container Won't Start
```bash
# Check logs
docker-compose logs <service-name>

# Rebuild from scratch
docker-compose down -v
docker-compose build --no-cache
docker-compose up
```

### Database Connection Issues
```bash
# Verify MySQL is healthy
docker-compose ps mysql

# Check connection from app container
docker-compose exec app nc -zv mysql 3306

# Verify credentials in .env file
```

### Out of Disk Space
```bash
# Clean up unused Docker resources
docker system prune -a --volumes

# Remove stopped containers
docker container prune

# Remove unused images
docker image prune -a
```

### Health Check Failing
```bash
# Check health status
docker inspect --format='{{json .State.Health}}' authentication-app

# Test endpoint manually
curl http://localhost:8080/api/v1/actuator/health

# Increase start_period if app needs more time
# Edit healthcheck.start_period in docker-compose.yml
```

---

## Performance Tuning

### JVM Optimization
The production Dockerfile includes tuned JVM settings:
- Container-aware memory allocation
- G1 garbage collector
- Heap dump on OOM
- Optimized for containers

### MySQL Optimization
Consider adding to docker-compose.yml for production:
```yaml
environment:
  - MYSQL_INNODB_BUFFER_POOL_SIZE=1G
  - MYSQL_MAX_CONNECTIONS=100
```

### Redis Optimization
Already configured with:
- Alpine Linux (smaller image)
- Data persistence enabled
- Adequate memory limits

---

## Security Considerations

### Production Checklist
- [ ] Change all default passwords
- [ ] Use strong JWT_SECRET (256+ bits)
- [ ] Enable SSL/TLS for MySQL connection
- [ ] Set up Redis password authentication
- [ ] Use Docker secrets instead of .env
- [ ] Implement rate limiting
- [ ] Enable audit logging
- [ ] Regular security updates
- [ ] Use specific image digests (not tags)
- [ ] Scan images with `docker scan`

### Network Security
- All services on private bridge network
- Only app container exposes ports externally
- Inter-service communication via Docker DNS

---

## Upgrading

### Update Service Versions

**Edit docker-compose.yml:**
```yaml
services:
  mysql:
    image: mysql:8.0.35  # Specify exact version
  redis:
    image: redis:7.2.4-alpine
```

**Test Upgrade:**
```bash
# Pull new images
docker-compose pull

# Start with new versions
docker-compose up -d

# Verify health
docker-compose ps
curl http://localhost:8080/api/v1/actuator/health
```

### Rolling Back
```bash
# Stop services
docker-compose down

# Edit docker-compose.yml to previous versions
# Or checkout previous commit

# Start services
docker-compose up -d
```

---

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Docker Build
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build and test
        run: |
          docker-compose -f docker-compose.test.yml up --abort-on-container-exit
```

### Production Deployment
- Use `Dockerfile` (not `Dockerfile.dev`)
- Remove development volume mounts
- Set SPRING_PROFILES_ACTIVE=prod
- Use managed databases (RDS, Azure Database)
- Implement proper secrets management

---

## Additional Resources

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
- [Spring Boot Docker Guide](https://spring.io/guides/topicals/spring-boot-docker/)
- [MySQL Docker Hub](https://hub.docker.com/_/mysql)
- [Redis Docker Hub](https://hub.docker.com/_/redis)

---

## Support

For project-specific issues:
- Check application logs: `docker-compose logs app`
- Review validation report: `_bmad-output/implementation-artifacts/docker-validation-report.md`
- Consult project documentation: `docs/`

For Docker issues:
- Run diagnostics: `docker system info`
- Check Docker daemon: `docker ps`
- Review system resources: `docker stats`
