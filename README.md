# Employee Login (by contract type)

Login system for three employee contract types: **full-time**, **part-time**, **mini-job**.  
DB model + authentication only (no other features).

## Run

1. Open the `employee-login` project in IntelliJ.
2. Run `EmployeeLoginApplication`.
3. Open **http://localhost:8080/login**.

## Test login

Use these seeded accounts (password for all: **password**):

| Username  | Contract  |
|-----------|-----------|
| fulltime  | Full-time |
| parttime  | Part-time |
| minijob   | Mini-job  |

- Correct credentials → redirect to `/home` with welcome message and contract type.
- Wrong credentials → stay on `/login` with error: *"Invalid username or password. Please try again."*

## Tech

- Spring Boot 3.4, Spring Security (form login), Spring Data JPA, Thymeleaf, H2.

## Database design

See [DATABASE-DESIGN.md](DATABASE-DESIGN.md) for table layout and login flow.
