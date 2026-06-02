# System Architecture Description

**Employee Login – Shift & Vacation Management System**  
Document for Bachelor thesis documentation and architecture diagrams.

---

## 1. Overall Architecture of the System

The application follows a **client–server architecture** with a **layered backend** and a **hybrid frontend** (server-rendered pages + REST API for dynamic features).

### 1.1 Architectural Styles

| Style | Description |
|-------|-------------|
| **Client–Server** | Browser (client) communicates with a single Spring Boot server. No separate API gateway or microservices. |
| **MVC (Model–View–Controller)** | Backend uses Spring MVC: **Controllers** handle HTTP, **Services** contain business logic, **domain entities** are the model; **Views** are Thymeleaf HTML templates. |
| **Layered (N-tier)** | Backend is structured in clear layers: **Presentation (Controllers)** → **Service (Business logic)** → **Repository (Data access)** → **Database**. |
| **REST API** | Part of the frontend (shift calendar, vacation calendar) uses **REST endpoints** (`/api/*`) returning JSON; the rest is **form-based** server-rendered pages. |

### 1.2 Technology Stack

- **Backend:** Java 17+, Spring Boot 3.x, Spring Security, Spring Data JPA, Thymeleaf
- **Frontend:** HTML/CSS/JavaScript, FullCalendar (calendar UI)
- **Database:** MySQL (configurable via `application-mysql.properties`), schema managed by Flyway (V1–V8)
- **Build:** Maven

### 1.3 High-Level Component Diagram (Conceptual)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Browser)                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ Login/      │  │ Dashboard   │  │ Shifts      │  │ Vacation        │ │
│  │ Register    │  │ (Home,      │  │ (FullCal)   │  │ (FullCal)       │ │
│  │             │  │  Contract)  │  │ + REST API  │  │ + REST API      │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘ │
│         │                │                │                    │          │
│         │  Form POST      │  Page GET      │  fetch() JSON      │ fetch()  │
└─────────┼────────────────┼────────────────┼────────────────────┼──────────┘
          │                │                │                    │
          ▼                ▼                ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION (Server)                      │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │ Presentation: Controllers (MVC + REST)                               ││
│  │   HomeController, AuthController, DashboardController,               ││
│  │   HelpController, AdminController, AdminVacationRequestController,  ││
│  │   AdminShiftPlanController, ShiftsController, VacationRequestController,││
│  │   HolidayController, ApiAuthController, ApiExceptionHandler          ││
│  └─────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │ Security: SecurityConfig, EmployeeUserDetailsService,               ││
│  │   RoleBasedAuthenticationSuccessHandler, EmployeeUserDetails         ││
│  └─────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │ Service Layer: ShiftPlanService, VacationRequestService,             ││
│  │   HolidayService, AdminShiftPlanService, AdminVacationRequestService,││
│  │   SupportService, RegistrationService                               ││
│  └─────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │ Repository Layer: JPA Repositories (Employee, ShiftPlan,             ││
│  │   ShiftEntry, VacationRequest, VacationRequestDay, SupportRequest) ││
│  └─────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Database (MySQL): users, shift_plans, shift_entries,                   │
│  vacation_requests, vacation_request_days, support_requests            │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Frontend–Backend Communication

### 2.1 Communication Patterns

| Use case | Pattern | Details |
|----------|---------|---------|
| **Login, Register, Help form, Admin approve/reject** | Form POST | HTML form submits to server; server responds with redirect and server-rendered page. |
| **Shifts calendar, Vacation calendar, Registration (optional)** | REST API + `fetch()` | JavaScript calls `/api/*` with `fetch()`, sends/receives JSON. |

### 2.2 REST API Usage

- **Base URL:** Same origin as the app (e.g. `/api/shifts`, `/api/vacation-request`, `/api/holidays`, `/api/auth/register`).
- **Authentication:** Session-based. After login, session cookie is sent automatically; no JWT or Bearer tokens.
- **CSRF:** For state-changing requests (POST, DELETE), the frontend reads CSRF token from `<meta name="_csrf">` and `<meta name="_csrf_header">` (Thymeleaf) and sends it in the request header (e.g. `X-CSRF-TOKEN`). Forms use hidden `_csrf` input.
- **Credentials:** `fetch(..., { credentials: 'same-origin' })` so cookies are sent.

### 2.3 API Endpoints Summary

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/shifts/window` | GET | Load shift planning window (entries, holidays, plan status). |
| `/api/shifts/entry` | POST | Add single shift entry (optional; UI also uses batch submit/draft). |
| `/api/shifts/entry/{id}` | DELETE | Delete one shift entry. |
| `/api/shifts/draft` | POST | Save shift plan as draft (body: `{ entries: [...] }`). |
| `/api/shifts/submit` | POST | Submit shift plan for admin approval. |
| `/api/shifts/presets` | GET | Get shift presets (e.g. morning/night). |
| `/api/vacation-request` | GET | Get vacation request for year (entitlement, selected days, status). |
| `/api/vacation-request/days` | POST | Add vacation day (body: `{ date: "YYYY-MM-DD" }`). |
| `/api/vacation-request/days/{date}` | DELETE | Remove vacation day. |
| `/api/vacation-request/submit` | POST | Submit vacation request for approval. |
| `/api/holidays` | GET | Get public holidays (query: `year`, `region=DE`). |
| `/api/auth/register` | POST | Register new user (JSON); optional auto-login. |

---

## 3. Backend Controllers and Responsibilities

### 3.1 MVC Controllers (HTML / Redirects)

| Controller | Package | Base path | Responsibility |
|------------|---------|-----------|-----------------|
| **HomeController** | `controller` | — | `GET /` → redirect to `/home`; `GET /home` → `home.html`. |
| **AuthController** | `controller` | — | `GET /login` → login page; `POST /register` → form registration, redirect. |
| **DashboardController** | `controller` | `/dashboard` | Serves dashboard pages: home, shifts, holidays, contract, message. |
| **HelpController** | `controller` | `/help` | `GET /` → help page; `POST /` → submit support request (form). |
| **AdminController** | `controller` | `/admin` | `GET /dashboard` → admin dashboard. |
| **AdminVacationRequestController** | `controller` | `/admin/vacation-requests` | List vacation requests; detail; `POST /{id}/approve`, `POST /{id}/reject`. |
| **AdminShiftPlanController** | `controller` | `/admin/shift-plans` | List shift plans; detail; `POST /{id}/approve`, `POST /{id}/reject`. |
| **DebugController** | `controller` | `/debug` | `GET /calendar` → minimal FullCalendar debug page. |

### 3.2 REST API Controllers (JSON)

| Controller | Package | Base path | Responsibility |
|------------|---------|-----------|-----------------|
| **ShiftsController** | `controller.api` | `/api/shifts` | Shift window, add/delete entry, save draft, submit, presets. |
| **VacationRequestController** | `controller.api` | `/api/vacation-request` | Get request, add/remove days, submit. |
| **HolidayController** | `controller.api` | `/api/holidays` | Return holidays for year/region (no DB). |
| **ApiAuthController** | `controller.api` | `/api/auth` | `POST /register` (JSON), optional auto-login. |

### 3.3 Global Exception Handling

- **ApiExceptionHandler** (`config`): `@RestControllerAdvice` for API exceptions; returns consistent error responses (e.g. JSON with `error` message).

---

## 4. Service Layer Structure and Main Services

Services contain business logic and orchestrate repositories. Controllers are thin and delegate to services.

### 4.1 Service Overview

| Service | Package | Responsibility |
|---------|---------|-----------------|
| **ShiftPlanService** | `service` | Planning window (allowed start/end), get window (entries + holidays + plan status), add/delete entry, save draft, submit; contract info and weekly summaries; allowed window and latest approved plan message. |
| **VacationRequestService** | `service` | Get/create draft vacation request per employee/year; add/remove day (with validation: entitlement, no shift conflict, DRAFT); submit; get dates with shifts (uses shift data). |
| **HolidayService** | `service` | Compute German public holidays (fixed + Easter-based); no database. |
| **AdminShiftPlanService** | `service` | List shift plans for admin; detail; approve/reject with reason. |
| **AdminVacationRequestService** | `service` | List vacation requests for admin; detail; approve/reject with reason. |
| **SupportService** | `service` | Persist support request (user id, category, message). |
| **RegistrationService** | `service` | Register user: validation, username uniqueness, BCrypt password, optional confirmation. |

### 4.2 Security (UserDetails)

- **EmployeeUserDetailsService** (`security`): Implements `UserDetailsService`; `loadUserByUsername` loads `Employee` and returns **EmployeeUserDetails**.
- **EmployeeUserDetails** (`security`): Wraps `Employee`; grants `ROLE_USER` always, `ROLE_ADMIN` when `employee.isAdmin()`.

### 4.3 Backend Layered Architecture (Diagram-Oriented)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER                                                      │
│  Controllers (MVC + REST) – handle HTTP, validate input, return view/JSON│
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  SERVICE LAYER (Business logic)                                          │
│  ShiftPlanService, VacationRequestService, HolidayService,               │
│  AdminShiftPlanService, AdminVacationRequestService,                     │
│  SupportService, RegistrationService                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  REPOSITORY LAYER (Data access)                                          │
│  EmployeeRepository, ShiftPlanRepository, ShiftEntryRepository,          │
│  VacationRequestRepository, VacationRequestDayRepository,               │
│  SupportRequestRepository                                               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  DATABASE (MySQL)                                                        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Repository / Database Layer Structure

### 5.1 Repositories

All extend `JpaRepository<Entity, Long>`. Custom methods are used for filtering and ordering.

| Repository | Entity | Notable methods |
|------------|--------|-----------------|
| **EmployeeRepository** | Employee | `findByUsername`, `existsByUsername`, `existsByAdminTrue` |
| **ShiftPlanRepository** | ShiftPlan | `findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc`, `findAllByOrderByUpdatedAtDesc`, `findByUserIdAndDateInPeriod`, `findFirstByUserIdAndStatusOrderByUpdatedAtDesc` |
| **ShiftEntryRepository** | ShiftEntry | `findByPlanIdOrderByEntryDateAscStartTimeAsc`, `findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc`, `deleteByPlanId` |
| **VacationRequestRepository** | VacationRequest | `findByEmployeeIdAndYear`, `existsByEmployeeIdAndYear`, `findAllByOrderByUpdatedAtDesc` |
| **VacationRequestDayRepository** | VacationRequestDay | `findByVacationRequestIdOrderByRequestDateAsc`, `findByVacationRequestIdAndRequestDate`, `deleteByVacationRequestIdAndRequestDate`, `existsByVacationRequestIdAndRequestDate` |
| **SupportRequestRepository** | SupportRequest | Default JPA methods |

### 5.2 Schema Management

- **Flyway** migrations in `src/main/resources/db/migration/` (V1–V8) define and evolve the schema (e.g. users, shift_plans, shift_entries, vacation_requests, vacation_request_days, support_requests, admin flag, approval fields).

---

## 6. Main Entities in the Database

### 6.1 Entity Overview

| Entity | Table | Description |
|--------|--------|-------------|
| **Employee** | `users` | User account: username, password hash, contract type, display name, allowed hours (week/month), vacation entitlement, admin flag, timestamps. |
| **ShiftPlan** | `shift_plans` | One planning period per user: period start/end, status (DRAFT/SUBMITTED/APPROVED/REJECTED), submitted/reviewed timestamps, reviewer id, rejection reason. |
| **ShiftEntry** | `shift_entries` | Single shift in a plan: plan id, date, start/end time, shift type, minutes. |
| **VacationRequest** | `vacation_requests` | One per employee per year: year, status (DRAFT/SUBMITTED/APPROVED/REJECTED), submitted/reviewed timestamps, reviewer id, rejection reason. |
| **VacationRequestDay** | `vacation_request_days` | One row per requested vacation date; links to vacation_requests. |
| **SupportRequest** | `support_requests` | Support ticket: user id, username input, category, message, status (e.g. OPEN). |

### 6.2 Enums (Domain)

- **ContractType:** Contract type (e.g. full-time, part-time, mini-job); drives default hours and vacation.
- **ShiftType:** MORNING, NIGHT, CUSTOM (or similar).
- **ShiftPlanStatus:** DRAFT, SUBMITTED, APPROVED, REJECTED.
- **VacationRequestStatus:** DRAFT, SUBMITTED, APPROVED, REJECTED.
- **RequestStatus:** For support requests (e.g. OPEN).
- **IssueCategory:** Support request category.

### 6.3 Database Entity Relationship (Diagram-Oriented)

```
  ┌──────────────┐
  │   users      │
  │ (Employee)   │
  │ id (PK)      │
  │ username     │
  │ ...         │
  └──────┬───────┘
         │ 1
         │
         │ *                    ┌─────────────────────┐
         ├─────────────────────│ shift_plans         │
         │                     │ id (PK)             │
         │                     │ user_id (FK)        │
         │                     │ period_start/end    │
         │                     │ status              │
         │                     └──────────┬──────────┘
         │                                │ 1
         │                                │
         │                                │ *
         │                     ┌─────────▼──────────┐
         │                     │ shift_entries      │
         │                     │ id (PK)            │
         │                     │ plan_id (FK)       │
         │                     │ entry_date, times  │
         │                     └────────────────────┘
         │
         │ *                    ┌─────────────────────┐
         ├─────────────────────│ vacation_requests   │
         │                     │ id (PK)             │
         │                     │ employee_id (FK)    │
         │                     │ year, status        │
         │                     └──────────┬──────────┘
         │                                │ 1
         │                                │ *
         │                     ┌─────────▼──────────────────┐
         │                     │ vacation_request_days      │
         │                     │ id (PK)                    │
         │                     │ vacation_request_id (FK)   │
         │                     │ request_date               │
         │                     └────────────────────────────┘
         │
         │ *                    ┌─────────────────────┐
         └─────────────────────│ support_requests    │
                               │ id (PK)             │
                               │ user_id (FK)        │
                               │ category, message   │
                               └─────────────────────┘
```

---

## 7. Authentication and Authorization (Internal)

### 7.1 Authentication

- **Mechanism:** Spring Security **form-based login**.
- **Login URL:** Custom `loginPage("/login")`. Form POST goes to Spring Security’s default `/login` (username/password).
- **User loading:** **EmployeeUserDetailsService** loads **Employee** by username and returns **EmployeeUserDetails** (wrapping the entity and exposing authorities).
- **Password storage:** **BCrypt** (PasswordEncoder bean in SecurityConfig).
- **Session:** HTTP session after successful login; session cookie sent on subsequent requests.

### 7.2 Authorization (Access Control)

- **Rules (SecurityConfig):**
  - **Permit all:** `/login`, `/register`, `/help`, `/api/auth/register`, `/css/**`, `/js/**`, `/debug/**`
  - **Role ADMIN:** `/admin/**` (all admin pages and actions)
  - **Authenticated:** any other request
- **Roles:** Every authenticated user has `ROLE_USER`; users with `Employee.admin == true` also have `ROLE_ADMIN`.
- **Post-login redirect:** **RoleBasedAuthenticationSuccessHandler** sends admins to `/admin/dashboard`, others to `/dashboard` (or configured default).

### 7.3 Flow (Diagram-Oriented)

```
User → GET /login → Login page
User → POST /login (username, password) → Spring Security
  → EmployeeUserDetailsService.loadUserByUsername(username)
  → PasswordEncoder.matches(password, passwordHash)
  → Success: RoleBasedAuthenticationSuccessHandler
      → if ROLE_ADMIN → redirect /admin/dashboard
      → else → redirect /dashboard
  → Failure: redirect /login?error=true
```

---

## 8. FullCalendar: How It Retrieves and Sends Data

### 8.1 Shifts Page (`/dashboard/shifts`)

- **Initial load:** `loadWindow()` calls `GET /api/shifts/window`. Response contains planning window, shift entries, holidays in window, and plan status. Entries are converted to FullCalendar events and stored in client state (`state.shiftEvents`); holidays in `state.holidayEvents`.
- **Event sources:**
  - **Shifts:** `state.shiftEvents` (from window + local additions).
  - **Holidays (window):** `state.holidayEvents` from window.
  - **Holidays (range):** Optional source that fetches `GET /api/holidays?year=...&region=DE` for the visible date range.
- **Adding shifts:** User selects day/preset; new events are added to `state.shiftEvents` (client-side). Persistence: **Submit** or **Save draft** sends full list via `POST /api/shifts/submit` or `POST /api/shifts/draft` with `{ entries: [...] }` built from state. Optional: single shift can be added with `POST /api/shifts/entry`.
- **Deleting shift:** If event has server id, `DELETE /api/shifts/entry/{id}` then `loadWindow()` to refresh.
- **Editing:** Drag/resize updates `state.shiftEvents`; persisted again on submit/draft. No per-event PATCH.
- **Read-only:** When plan status is not DRAFT, editing is disabled (`canEditPlan()`).

### 8.2 Vacation (Holidays) Page (`/dashboard/holidays`)

- **Initial load:** `loadRequest()` calls `GET /api/vacation-request?year=...`. Response contains entitlement, selected dates, status, and (e.g.) dates with shifts. Selected dates are stored in `state.request.selectedDates`.
- **Event source:** One source that returns events from `state.request.selectedDates` (vacation days shown on calendar).
- **Add day:** User clicks date → `POST /api/vacation-request/days` with `{ date: "YYYY-MM-DD" }` → then refresh request or optimistic update and `refetchEvents` / `changeView`.
- **Remove day:** `DELETE /api/vacation-request/days/{date}` → then refresh.
- **Submit:** `POST /api/vacation-request/submit?year=...` to send request for approval.

### 8.3 Data Flow Summary (Diagram-Oriented)

```
Shifts calendar:
  Page load → GET /api/shifts/window → DTO (entries, holidays, status)
    → entriesToEvents() → state.shiftEvents
    → calendar refetchEvents()
  User adds/edits locally → state.shiftEvents
  Submit / Save draft → buildEntriesFromState() → POST /api/shifts/submit or /draft
  Delete one → DELETE /api/shifts/entry/{id} → loadWindow()

Vacation calendar:
  Page load → GET /api/vacation-request?year=... → DTO (selectedDates, entitlement, status)
    → state.request.selectedDates → event source
  Add day → POST /api/vacation-request/days → loadRequest() or optimistic + refetchEvents
  Remove day → DELETE /api/vacation-request/days/{date} → idem
  Submit → POST /api/vacation-request/submit?year=...
```

---

## 9. Main Application Modules

| Module | Description | Main backend components | Main frontend |
|--------|-------------|-------------------------|----------------|
| **Shift management** | Employees plan shifts in a period; submit for approval; admin approves/rejects. | ShiftsController, ShiftPlanService, ShiftPlan/ShiftEntry, AdminShiftPlanController, AdminShiftPlanService | `dashboard/shifts.html`, `shifts.js`, FullCalendar |
| **Vacation management** | Employees select vacation days per year; submit; admin approves/rejects. | VacationRequestController, VacationRequestService, VacationRequest/VacationRequestDay, AdminVacationRequestController, AdminVacationRequestService | `dashboard/holidays.html`, `holidays.js`, FullCalendar |
| **Admin approval** | Admins see lists and details of shift plans and vacation requests; approve or reject with optional reason. | AdminVacationRequestController, AdminShiftPlanController, AdminVacationRequestService, AdminShiftPlanService | `admin/vacation-requests/*`, `admin/shift-plans/*` (Thymeleaf + forms) |
| **Support requests** | Users (or guests) submit help/support tickets. | HelpController, SupportService, SupportRequest | `help.html` (form POST to `/help`) |
| **Dashboard** | Authenticated user home: navigation, contract info, message placeholder. | DashboardController, ShiftPlanService (contract/window info) | `dashboard/layout.html`, `dashboard/home.html`, `contract.html`, `message.html` |
| **Authentication** | Login, registration (form and API), role-based redirect. | AuthController, ApiAuthController, RegistrationService, SecurityConfig, EmployeeUserDetailsService, RoleBasedAuthenticationSuccessHandler | `login.html`, `auth.js` |

---

## 10. Request Flow Example: Vacation Request (Submit → Backend → DB → Admin Approval)

End-to-end flow suitable for a **request flow diagram**:

1. **User selects vacation days** (e.g. on `/dashboard/holidays`):
   - Frontend: Add day → `POST /api/vacation-request/days` with `{ date: "YYYY-MM-DD" }`.
   - **VacationRequestController.addDay** → **VacationRequestService.addDay** (validates: DRAFT, entitlement, no shift conflict, year, not Sunday/past) → **VacationRequestDayRepository.save** (and getOrCreateDraft for VacationRequest if needed).

2. **User submits vacation request**:
   - Frontend: `POST /api/vacation-request/submit?year=...`.
   - **VacationRequestController.submit** → **VacationRequestService.submit** (validates: DRAFT, has at least one day) → set status to SUBMITTED, set submittedAt → **VacationRequestRepository.save**.

3. **Admin sees list**:
   - Admin opens `/admin/vacation-requests`.
   - **AdminVacationRequestController.list** → **AdminVacationRequestService.listForAdmin** → **VacationRequestRepository.findAllByOrderByUpdatedAtDesc** (and related data) → model → Thymeleaf view.

4. **Admin opens detail**:
   - GET `/admin/vacation-requests/{id}` → **AdminVacationRequestController.detail** → **AdminVacationRequestService.getDetailForAdmin(id)** → repositories (VacationRequest, VacationRequestDay, Employee) → detail view.

5. **Admin approves**:
   - Form POST to `/admin/vacation-requests/{id}/approve` (with CSRF).
   - **AdminVacationRequestController.approve** → **AdminVacationRequestService.approve(id, adminId)** → load VacationRequest, set status APPROVED, reviewedAt, reviewedById → **VacationRequestRepository.save** → redirect to detail with success message.

**Alternative: Admin rejects**  
- POST `/admin/vacation-requests/{id}/reject?reason=...` → **AdminVacationRequestController.reject** → **AdminVacationRequestService.reject(id, adminId, reason)** → status REJECTED, rejectionReason, reviewedAt, reviewedById → save → redirect.

This flow can be drawn as: **Browser** → **Controller** → **Service** → **Repository** → **Database**, with branch for approve vs reject.

### 10.1 Admin Shift Plan Review Flow (Approve / Reject)

1. **User submits shifts** (Select Shifts): `POST /api/shifts/submit` → **ShiftPlanService.submit** → plan status set to **PENDING_APPROVAL**; entries saved.
2. **Admin lists plans**: GET `/admin/shift-plans` → **AdminShiftPlanController.list** → **AdminShiftPlanService.listForAdmin** (pending first) → list view with **View** per plan.
3. **Admin opens detail**: GET `/admin/shift-plans/{id}` → **AdminShiftPlanService.getDetailForAdmin(id)** → detail view with employee, period, entries, status, **Approve** and **Reject** forms (only when status is PENDING_APPROVAL).
4. **Admin approves**: POST `/admin/shift-plans/{id}/approve` → **AdminShiftPlanService.approve(id, adminId)** → plan status **APPROVED**, reviewedAt, reviewedById → save → redirect to list with success message. In the **user calendar**, shifts of that plan are loaded with status APPROVED and displayed **green**.
5. **Admin rejects**: POST `/admin/shift-plans/{id}/reject?reason=...` → **AdminShiftPlanService.reject(id, adminId, reason)** → plan status **REJECTED**, rejectionReason, reviewedAt → save → redirect to list. In the **user calendar**, shifts are displayed **red**. The user can **edit or delete** those shifts and **submit again**; the backend reuses the REJECTED plan and sets it to PENDING_APPROVAL with the new entries.

**Calendar color convention (UX):** **Purple** = pending admin approval; **Green** = approved; **Red** = rejected. Implemented via `shift-status-pending`, `shift-status-approved`, `shift-status-rejected` (and badge classes) in `shifts.js` / `style.css`; entry status comes from `GET /api/shifts/window` (per-entry status from plan).

---

## Diagram Checklist for Your Thesis

You can create the following diagrams from this document:

1. **System architecture diagram** – Use Section 1.3: client, server layers, database.
2. **Backend layered architecture** – Use Section 4.3: Presentation → Service → Repository → Database.
3. **Request flow diagram** – Use Section 10: vacation request from submit through admin approval/rejection; optionally add shift plan flow similarly.
4. **Database entity overview** – Use Section 6: tables, main attributes, and Section 6.3 for relationships.
5. **FullCalendar data flow** – Use Section 8: shifts and vacation load/update/delete/submit flows and API calls.
6. **Authentication/authorization flow** – Use Section 7.3 and SecurityConfig rules in Section 7.2.

If you need more detail on a specific layer or a different flow (e.g. shift plan submit → admin approve), the same pattern applies: Controller → Service → Repository → Database, with the corresponding entities and endpoints listed above.
