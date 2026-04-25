# spring_project - Creator Economy SaaS Platform

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Supported-blue.svg)](https://www.docker.com/)

## Table of Contents

- [Description](#description)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [Contributing](#contributing)
- [Roadmap](#roadmap)
- [License](#license)
- [Contact](#contact)

## Description

**spring_project** is a unified creator economy SaaS platform designed to empower independent content creators managing audiences across multiple social media platforms (YouTube, TikTok, Instagram, Facebook). 

The platform addresses three critical pain points faced by content creators:
- **Fragmented Tooling**: Eliminates manual revenue reconciliation across platforms with unified tracking
- **Compliance Liability**: Provides systematic FTC/GDPR compliance with automated documentation and audit trails
- **Platform API Risk**: Features platform-agnostic core functionality with circuit breaker resilience

### Core Value Proposition

- **Single Workspace**: Schedule and publish content across all major platforms from one interface
- **Real-Time Revenue Tracking**: Unified analytics for affiliate marketing, platform ad revenue, and brand sponsorships  
- **Automated Compliance**: Human-supervised compliance documentation with full audit trails
- **Platform Independence**: Core business logic remains functional regardless of individual platform API availability

### Target Audience

Independent content creators with monetized presence on 2+ social media platforms, generating revenue through:
- Affiliate marketing
- Platform advertisement revenue 
- Brand sponsorships and partnerships

## Technology Stack

### Backend Framework
- **Java 17** - Modern LTS Java version
- **Spring Boot 3.3.5** - Application framework
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database abstraction layer
- **Spring Data JDBC** - JDBC abstraction
- **Spring Validation** - Bean validation support
- **Spring Mail** - Email functionality

### Database & Persistence
- **MySQL 8.0** - Primary database
- **Redis** - Caching and session management
- **Flyway** - Database migration management
- **H2** - In-memory database for testing

### Security & Authentication
- **JWT (JSON Web Tokens)** - Token-based authentication
- **JJWT 0.12.6** - JWT implementation library

### Development & Operations
- **Maven 3.9.6** - Dependency management and build tool
- **Docker & Docker Compose** - Containerization
- **Spring Boot DevTools** - Development utilities
- **Spring Boot Actuator** - Production monitoring
- **Lombok** - Code generation

### Testing
- **Spring Boot Test** - Testing framework
- **Spring Security Test** - Security testing utilities

## Prerequisites

Ensure you have the following installed on your system:

- **Java 17** or higher
- **Maven 3.9.6** or higher  
- **Docker** and **Docker Compose**
- **Git** for version control

## Installation

### Quick Start with Docker (Recommended)

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd spring_project
   ```

2. **Create environment file:**
   ```bash
   cp .env.example .env  # If .env.example exists, or create manually
   ```
   
   Create a `.env` file in the project root with the following variables:
   ```env
   # Database Configuration
   DB_URL=jdbc:mysql://mysql:3306/spring_project
   DB_USERNAME=root
   DB_PASSWORD=123456
   
   # JWT Configuration
   JWT_SECRET=your-super-secure-jwt-secret-key-here
   ```

3. **Start the application:**
   ```bash
   # For development (with live reload)
   docker compose up -d
   
   # For production build
   # docker compose -f docker-compose.yml up -d
   ```

4. **Verify the installation:**
   - API Health Check: http://localhost:8080/api/v1/actuator/health
   - Application: http://localhost:8080/api/v1/
   - MailHog UI: http://localhost:8025 (for email testing)

### Local Development Setup

1. **Database Setup:**
   ```bash
   # Start only the database services
   docker compose up -d mysql redis mailhog
   ```

2. **Configure local environment:**
   Update `src/main/resources/application-dev.yml` if needed for local database connection.

3. **Run the application locally:**
   ```bash
   # Using Maven wrapper (recommended)
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   
   # Or using Maven directly
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

4. **Enable debugging (optional):**
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
   ```

### Database Configuration

The application uses **MySQL 8.0** as the primary database with **Flyway** for migration management.

#### Database Schema Migration
- Migration scripts are located in: `src/main/resources/db/migration/`
- Flyway automatically runs migrations on application startup
- Database is created automatically by Docker Compose

#### Redis Configuration
Redis is used for:
- Session management
- Caching frequently accessed data
- Rate limiting (future implementation)

## Usage

After successful installation, you can access:

- **API Health Check**: http://localhost:8080/api/v1/actuator/health
- **Application**: http://localhost:8080/api/v1/
- **Email Testing Interface**: http://localhost:8025 (MailHog)

The application provides REST APIs under the `/api/v1` context path for:
- User registration and authentication
- Creator profile management
- Platform connections
- Health monitoring and metrics

## Project Structure

```
spring_project/
├── src/main/java/com/samuel/app/
│   ├── AppApplication.java          # Main Spring Boot application
│   ├── config/                     # Configuration classes
│   ├── creator/                    # Creator-specific modules
│   ├── exceptions/                 # Custom exception handling
│   └── shared/                     # Shared utilities and components
├── src/main/resources/
│   ├── application.yml             # Main application configuration
│   ├── application-dev.yml         # Development configuration
│   └── db/migration/              # Flyway database migrations
├── src/test/                      # Test classes and resources
├── _bmad/                         # BMad framework artifacts
├── _bmad-output/                  # Generated documentation and artifacts
│   ├── planning-artifacts/        # PRD, architecture, epics
│   └── implementation-artifacts/  # Implementation stories and status
├── docs/                          # Project documentation
├── target/                        # Maven build artifacts
├── docker-compose.yml            # Docker services configuration
├── Dockerfile                     # Production Docker image
├── Dockerfile.dev               # Development Docker image
└── pom.xml                       # Maven project configuration
```

### Architecture Highlights

- **Modular Design**: Creator-specific functionality organized in dedicated modules
- **Configuration Management**: Environment-specific configurations with Spring Profiles
- **Exception Handling**: Centralized exception handling in dedicated package
- **Shared Components**: Reusable utilities and components in shared package

## Development

### Building the Project

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Package the application
./mvnw clean package

# Skip tests during build (not recommended)
./mvnw clean package -DskipTests
```

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=AuthControllerTest

# Run tests with coverage (if configured)
./mvnw test jacoco:report
```

### Development Environment

1. **Hot Reload**: The development Docker container supports live code reloading
2. **Debug Port**: Debug port `5005` is exposed for IDE attachment
3. **Database Access**: MySQL is accessible on `localhost:3306`
4. **Email Testing**: MailHog provides email testing interface at `http://localhost:8025`

### Security Configuration

- **JWT Authentication**: Stateless authentication with configurable token expiry
- **Password Security**: BCrypt password encoding
- **Rate Limiting**: Account lockout after failed login attempts
- **CORS**: Configurable cross-origin resource sharing

### Email Configuration

Development uses **MailHog** for email testing:
- SMTP Server: `localhost:1025`
- Web Interface: `http://localhost:8025`
- No authentication required in development mode

## Testing

The project includes comprehensive test coverage:

- **Unit Tests**: Individual component testing
- **Integration Tests**: Full application context testing
- **Security Tests**: Authentication and authorization testing
- **Contract Tests**: API contract validation

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=AuthControllerTest

# Run tests with coverage (if configured)
./mvnw test jacoco:report
```

### Test Reports

Test reports and coverage information can be found in:
- `target/surefire-reports/` - Test execution reports
- `target/site/jacoco/` - Code coverage reports (if configured)

## Deployment

### Production Deployment with Docker

1. **Build production image:**
   ```bash
   docker compose -f docker-compose.yml up -d --build
   ```

2. **Environment Variables:**
   Ensure production environment variables are set:
   ```env
   DB_URL=jdbc:mysql://your-production-db:3306/spring_project
   DB_USERNAME=production_user
   DB_PASSWORD=secure_production_password
   JWT_SECRET=very-secure-production-jwt-secret
   SPRING_PROFILES_ACTIVE=prod
   ```

3. **Health Monitoring:**
   Monitor application health at: `/api/v1/actuator/health`

## Contributing

We welcome contributions to the spring_project platform! Please follow these guidelines:

1. **Fork the repository** and create a feature branch
2. **Follow coding standards** and maintain existing code style
3. **Write tests** for new functionality
4. **Update documentation** as needed
5. **Submit a pull request** with a clear description of changes

### Code Standards

- Follow Java naming conventions
- Use Lombok annotations to reduce boilerplate
- Write comprehensive JavaDoc for public APIs
- Maintain test coverage above 80%

## Roadmap

### Current Phase: MVP - Platform Foundation & Connections
- [x] User registration and email verification
- [x] JWT-based authentication system
- [ ] Creator profile creation and management
- [ ] Platform connection dashboard

### Next Phase: Growth - Content & Revenue
- [ ] Cross-platform content scheduling
- [ ] Revenue stream configuration
- [ ] Automated revenue data collection
- [ ] Performance analytics dashboard

### Vision Phase: Compliance & Advanced Features
- [ ] FTC/GDPR compliance tooling
- [ ] Automated compliance documentation
- [ ] Advanced analytics and reporting

## License

*License information to be added*

## Contact

*Contact information and support channels to be added*

---

**Built for the Creator Economy**

*Last Updated: April 25, 2026*