# Shift Planner — Employee Workforce Management System

A full-stack web application for managing employee scheduling, vacation requests, overtime, and internal communication — built with Java 21 and Spring Boot 3.4.4.

---

## Features

### Employee Side
| Feature | Description |
|---|---|
| Login / Register | Account creation with contract type selection |
| Dashboard | Overview of current/next month shifts, plan status, and vacation summary |
| Shift Planning | Calendar UI to create, edit, and submit shift plans for approval |
| Vacation Planning | Calendar UI to select and submit vacation day requests |
| Contract Info | View allowed hours per week/month and vacation entitlement |
| Overtime Inbox | View, accept, or decline overtime requests assigned by admin |
| Internal Messaging | Chat with other employees and admins |
| Profile | Update personal info and profile picture |
| Support | Submit help requests by category |

### Admin Side
| Feature | Description |
|---|---|
| Admin Dashboard | Summary overview with recent overtime activity |
| Shift Plan Review | View, approve, reject, or request changes on submitted shift plans |
| Vacation Request Review | Review individual vacation requests with full day inspection |
| Overtime Management | Create overtime requests and track employee responses |
| Admin Messaging | Internal chat in admin layout |
| Admin Profile | Manage admin account info |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.4 |
| Security | Spring Security (session-based form login, CSRF, roles) |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL (production), H2 (local/test) |
| Migrations | Flyway |
| Templating | Thymeleaf + thymeleaf-extras-springsecurity6 |
| Frontend | Vanilla JavaScript (`fetch` + CSRF headers), custom CSS |
| Calendar UI | FullCalendar 6.1.10 (CDN) |
| PDF Export | Apache PDFBox 3.0.2 |
| Build | Maven |

---

## Architecture

```
Controller → Service → Repository → Entity
```

- **Monolithic** Spring Boot application
- **Session-based** authentication (not JWT)
- Two access areas: `/dashboard/*` (employees) and `/admin/*` (admins)
- SQL schema managed via **Flyway migrations** (`src/main/resources/db/migration/`)

---

## Getting Started

### Prerequisites
- Java 21
- MySQL 8+ **or** no database (H2 option below)
- Maven installed, or use IntelliJ IDEA

---

### Option A — Run with MySQL (default)

**1. Create the database:**
```sql
CREATE DATABASE IF NOT EXISTS shiftplanner
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**2. Set your credentials** in `src/main/resources/application-mysql.properties`:
```properties
spring.datasource.username=root
spring.datasource.password=your_password
```

**3. Start the app:**
```bash
# from the employee-login/ directory
mvn spring-boot:run
```

**4. Open in browser:**
```
http://localhost:8080/login
```

> Flyway will automatically run all migrations on startup.

---

### Option B — Run with H2 (no MySQL needed)

Fastest option for local development — no database installation required.

```bash
# IntelliJ: add VM option
-Dspring.profiles.active=local

# or CLI:
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Uses an in-memory H2 database (`jdbc:h2:mem:employeedb`). Data resets on every restart.

---

### Troubleshooting

| Problem | Fix |
|---|---|
| Port 8080 already in use | Change `server.port` in `application.properties` |
| MySQL login error | Check username/password and that MySQL is running on `127.0.0.1:3306` |
| `mvn` not found | Install Maven or run directly from IntelliJ |

---

## Project Structure

```
employee-login/
├── src/
│   ├── main/
│   │   ├── java/          # Controllers, Services, Repositories, Entities
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-mysql.properties
│   │       ├── application-local.properties
│   │       ├── db/migration/       # Flyway SQL migrations
│   │       └── templates/          # Thymeleaf HTML templates
│   └── test/
└── pom.xml
```

---

## Configuration Files

| File | Purpose |
|---|---|
| `application.properties` | Port, default active profile, planning window settings |
| `application-mysql.properties` | MySQL connection + Flyway enabled |
| `application-local.properties` | H2 in-memory config for local dev |

---

## Testing

```bash
mvn test
```

The project includes a Spring Boot integration test that verifies the full application context loads successfully using an H2 in-memory database (Flyway disabled for tests).

> Unit tests for individual services and controllers are planned for a future iteration.

---

## License

This project is for portfolio and educational purposes.
