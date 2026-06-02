# Database design: employees and login

## Contract types (enum in application)

| Value     | Description        | Default hours/week | Default hours/month |
|----------|--------------------|--------------------|----------------------|
| FULL_TIME| Full-time (Vollzeit) | 40                 | —                    |
| PART_TIME| Part-time (Teilzeit)| 20                 | —                    |
| MINI_JOB | Mini-job (Minijob)  | ~10                | 42                   |

## Table: `employees`

| Column                  | Type            | Constraints  | Description |
|-------------------------|-----------------|-------------|-------------|
| id                      | BIGINT          | PK, identity| Primary key |
| username                | VARCHAR(100)    | NOT NULL, UNIQUE | Login username |
| password_hash           | VARCHAR(255)    | NOT NULL    | BCrypt-hashed password |
| contract_type           | VARCHAR(20)     | NOT NULL    | FULL_TIME, PART_TIME, MINI_JOB |
| display_name            | VARCHAR(200)    | nullable    | Optional display name |
| allowed_hours_per_week  | DECIMAL(5,2)   | nullable    | Max working hours per week (default from contract type) |
| allowed_hours_per_month | DECIMAL(5,2)   | nullable    | Max working hours per month (Mini-job: 42; others null) |

- Passwords are never stored in plain text; only the BCrypt hash is persisted.
- Each username is unique. One employee can have only one contract type.
- Working-hour limits are stored per employee; on create they are set from the contract type defaults and can be overridden later for shift planning validation.

## Login flow

1. User opens `/login`, enters username and password.
2. Form is POSTed to `/login` (Spring Security).
3. `EmployeeUserDetailsService` loads `Employee` by `username` from DB.
4. Spring Security compares submitted password with `password_hash` using BCrypt.
5. If they match → user is authenticated and redirected to `/home`.
6. If they do not match → redirect to `/login?error=true`; the page shows an error notification.

## Test accounts (seeded on startup)

| Username  | Password  | Contract type |
|-----------|-----------|---------------|
| fulltime  | password  | FULL_TIME     |
| parttime  | password  | PART_TIME     |
| minijob   | password  | MINI_JOB      |
