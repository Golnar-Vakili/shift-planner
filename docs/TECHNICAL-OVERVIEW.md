# Technological Foundations — Technical Overview

This document provides a concise technical overview of the **employee-login** application for use in a "Technological Foundations" or "System Architecture" section of a thesis.

---

## 1. Backend Technology

- **Language:** Java 21  
- **Framework:** Spring Boot 3.4.4  
- **Main components:**
  - **Spring Web** — MVC and REST APIs  
  - **Spring Data JPA** — persistence and repositories  
  - **Spring Security** — authentication and authorization  
  - **Spring Validation** — request validation  
  - **Thymeleaf** — server-side HTML templating  

The backend is structured in layers: **controllers** (web and API), **services** (business logic), **repositories** (data access), and **domain** entities. Admin and employee areas are separated by URL prefixes and roles.

---

## 2. Frontend Technology

- **Server-rendered UI:** Thymeleaf templates with a shared dashboard layout and page-specific fragments.  
- **Client-side scripting:** Vanilla JavaScript (no frontend framework).  
- **Libraries:**
  - **FullCalendar 6.1.10** (CDN) — calendar UI for shift planning and vacation day selection (with interaction and German locale).  
- **Styling:** Custom CSS (`style.css`); no Bootstrap or other UI framework.  
- **API usage:** JavaScript uses `fetch` with CSRF headers to call REST endpoints for shifts, vacation requests, and holidays; responses drive UI updates and toasts.

---

## 3. Database System

- **Primary database:** MySQL (e.g. `shiftplanner` on `127.0.0.1:3306`).  
- **Development/testing:** H2 is on the classpath for tests.  
- **Schema management:** Flyway (migrations in `src/main/resources/db/migration/`).  
- **ORM:** JPA/Hibernate with `ddl-auto=validate` in production so the schema is defined only by Flyway scripts.  
- **Persistence:** JPA entities map to tables such as `users`, `shift_plans`, `shift_entries`, `vacation_requests`, `vacation_request_days`, `support_requests`.

---

## 4. Authentication Method

- **Type:** Session-based form login (no JWT or OAuth in this project).  
- **Flow:** User submits username/password on `/login`; Spring Security authenticates against the database via a custom `UserDetailsService` that loads the `Employee` entity.  
- **Password storage:** BCrypt (Spring Security’s `BCryptPasswordEncoder`).  
- **Roles:** Every authenticated user has `ROLE_USER`; users with `Employee.admin == true` also have `ROLE_ADMIN`.  
- **Post-login redirect:** Custom success handler sends admins to `/admin/dashboard` and other users to `/dashboard`.  
- **Protected routes:** `/admin/**` requires `ROLE_ADMIN`; all other authenticated routes require any authenticated user.  
- **CSRF:** Enabled for form and API requests; tokens are supplied via meta tags and request headers/body.

---

## 5. Chat / Communication System

The application does **not** implement real-time chat. It provides a **support/contact request** flow:

- **Entry point:** Public `/help` page (and dashboard “Send Message” area).  
- **Behavior:** User selects an issue category and submits a text message.  
- **Backend:** `HelpController` and `SupportService` create a `SupportRequest` (user id, optional username/email, category, message, status) and persist it in the `support_requests` table.  
- **Model:** One-way submission; no built-in reply UI or real-time notifications.  
- **Access:** Stored for later processing (e.g. by admins or external tools); the thesis can describe this as a “support request” or “contact form” subsystem rather than chat.

---

## 6. Overall System Architecture

- **Style:** **Client–server** with a **REST API** used by the server-rendered frontend.  
- **MVC on the server:**  
  - **Controllers** handle HTTP (page requests return Thymeleaf view names; API controllers return JSON).  
  - **Services** contain business logic.  
  - **Repositories** abstract database access.  
- **REST API:** JSON over HTTP; used by the same origin (e.g. `/api/shifts/*`, `/api/vacation-request/*`, `/api/holidays`, `/api/auth/register`).  
- **Security:** Centralized in Spring Security (filter chain, form login, role-based access).  
- **Deployment:** Single Spring Boot process; no separate API gateway or microservices.

---

## 7. Main Modules of the Application

| Module | Description |
|--------|-------------|
| **Authentication & registration** | Login, logout, user registration; role-based redirect; password encoding. |
| **Employee dashboard** | Home, contract type view, navigation to shifts, holidays, contract, and message. |
| **Shift management** | Planning window (configurable delay/window days), calendar selection of shifts, draft save, submit for approval; weekly progress display. |
| **Vacation (holiday) requests** | Year-based entitlement, calendar selection of vacation days, submit for approval; blocking of Sundays and days with shifts. |
| **Admin — vacation approval** | List and detail of vacation requests; approve/reject with optional reason; PENDING-first ordering. |
| **Admin — shift plan approval** | List and detail of shift plans; approve/reject with optional reason; exclusion of empty drafts from list. |
| **Holidays (public)** | API and UI for public holidays (e.g. DE) used to block vacation/shift dates. |
| **Support / contact** | Help form: category + message stored as support requests. |
| **Admin dashboard** | Entry and navigation for admin (vacation requests, shift plans). |

---

## 8. Important Libraries and Technologies

| Technology | Purpose |
|------------|---------|
| **Spring Boot 3.4** | Application framework and auto-configuration. |
| **Spring Security 6** | Authentication, authorization, CSRF, password encoding. |
| **Spring Data JPA** | Repositories and ORM. |
| **Thymeleaf + thymeleaf-extras-springsecurity6** | Server-side templates and security-aware conditionals. |
| **MySQL Connector/J** | JDBC driver for MySQL. |
| **Flyway** | Versioned database migrations. |
| **H2** | In-memory DB for tests. |
| **FullCalendar 6.1** | Calendar and interaction for shifts and holidays. |
| **Jakarta Validation** | Bean validation on API inputs. |
| **BCrypt** | Password hashing. |

---

*Document generated for thesis use. Adjust profile names (e.g. `mysql`) and database names (e.g. `shiftplanner`) to match your actual configuration if needed.*
