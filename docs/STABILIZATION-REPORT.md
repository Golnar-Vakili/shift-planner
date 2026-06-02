# User-Side Stabilization & Testing Report

**Scope:** Select Shifts, Select Holidays, Contract Overview, and related backend/DB.  
**Purpose:** Verify and harden the current employee workflow before building admin pages.  
**Date:** Pre-admin implementation.

---

## A. Stabilization / Testing Report

### 1. What is already working

#### Shift planning
- **Backend:** `ShiftPlanService` enforces: no shifts on Sundays (`DayOfWeek.SUNDAY`), no shifts on public holidays, dates must be inside planning window (`getAllowedWindowStart()` + `windowDays`). Weekly hours validation (80%–100%+2h) on submit. Add entry, delete entry, save draft, submit all validated.
- **API:** `GET /api/shifts/window` returns allowed range, entries, contract info, holidays, plan status. `POST /api/shifts/entry`, `DELETE /api/shifts/entry/{id}`, `POST /api/shifts/draft`, `POST /api/shifts/submit` return appropriate status/errors.
- **Frontend:** `shifts.js` loads window, renders FullCalendar, blocks Sundays/holidays/outside-window, add/delete/save draft/submit wired with toasts and `loadWindow()` refresh. Submit button enabled only when `isSubmitValid()` (weekly hours rule).

#### Holiday selection
- **Backend:** `VacationRequestService` enforces: only DRAFT can be modified; no past dates; current year only; no Sundays; no duplicate date; cannot exceed entitlement; no vacation on dates with shifts (`getDatesWithShifts`). Submit requires exactly `entitlementDays` selected and re-validates all days (past, year, Sunday).
- **API:** `GET /api/vacation-request?year=`, `POST /api/vacation-request/days` (body `{ date }`), `DELETE /api/vacation-request/days/{date}`, `POST /api/vacation-request/submit?year=` with clear error bodies.
- **Frontend:** `holidays.js` loads request, renders calendar with `validRange` current year, `dayCellClassNames` for Sunday/past/shift-blocked/selected. Add/remove with optimistic update and `loadRequest()` on success/error. Progress, status badge, submit-helper text, and button disabled state all driven by `state.request`.

#### Contract overview
- **Backend:** `DashboardController.contract()` builds `ContractOverviewDto` from `Employee` and `ShiftPlanService` (planning window). Contract type label, weekly/monthly hours, vacation days, planning window dates.
- **Frontend:** `contract.html` shows intro, summary grid, shift/vacation cards, footer. All values from `contractOverview`; read-only.

#### Database
- **Schema:** `users` (contract_type, allowed_hours_per_week/month, allowed_vacation_days), `shift_plans`, `shift_entries`, `vacation_requests`, `vacation_request_days` with FKs and indexes. V6 migration removes Sunday vacation/shift rows.
- **Uniqueness:** `(employee_id, year)` for vacation_requests; `(vacation_request_id, request_date)` for vacation_request_days. No duplicate request per employee/year or duplicate date per request.

---

### 2. What is still fragile or unclear

- **Vacation submit button:** The "Submit vacation request" button has **no click handler**. It is enabled when `canSubmit` is true but does not call `POST /api/vacation-request/submit`. **This is a critical gap.**
- **Stale shift-blocked dates:** `datesWithShifts` is loaded with the vacation request. If the user changes shifts (e.g. removes a shift) and does not refresh the holidays page, the calendar can still show that date as shift-blocked until next `loadRequest()`.
- **Multiple shift plans in window:** `findByUserIdAndPeriodStartBetween` can return more than one plan (e.g. overlapping or legacy). `getWindow` merges entries from all of them; `getOrCreateDraft` picks the first DRAFT. If there are multiple DRAFT plans, behavior is "first found" and could be surprising.
- **Contract overview when employee missing:** If for some reason `userDetails.getEmployee()` were null, the controller would NPE. In practice the security layer ensures an authenticated employee.

---

### 3. Edge cases and possible breakage

| Area | Edge case | Mitigation |
|------|-----------|------------|
| Holidays | Submit button not wired | **Must fix:** add click handler → `POST /api/vacation-request/submit` then `loadRequest()`. |
| Holidays | API returns `selectedDates` as array of objects (e.g. `{year,month,dayOfMonth}`) | Frontend uses `dateToStr(d)` which handles both string and object; Spring typically serializes `LocalDate` as `"yyyy-MM-dd"`. If server sent object form, `dateToStr` in holidays.js handles it (see `d.year`, `d.monthValue`, `d.dayOfMonth` in shifts.js). holidays.js only uses string path; if API sends string, no issue. |
| Holidays | User has exactly entitlement selected but one day is Sunday (legacy data) | Backend submit() iterates all days and throws if any Sunday. Safe. |
| Shifts | User submits with weekly hours just under 80% | Backend throws with clear message. Frontend `isSubmitValid()` should disable button; worth testing. |
| Shifts | Planning window crosses midnight / timezone | Window uses `LocalDate`; no timezone in logic. Safe for same-timezone use. |
| Contract | Employee has null `allowedHoursPerWeek` | Controller uses `formatHours` and fallback; overview uses employee values. ContractType defaults set on registration; DB has NOT NULL. Low risk. |

---

### 4. Database consistency

| Table | Status storage | Duplicates / invalid rows | Notes |
|-------|----------------|---------------------------|--------|
| **users** | N/A | contract_type VARCHAR(20), allowed_* from contract/vacation | Stable. |
| **shift_plans** | `status` DRAFT/PENDING/APPROVED/REJECTED | No unique on (user_id, period). Multiple plans per user/window possible. | Acceptable; getOrCreateDraft uses first DRAFT in window. |
| **shift_entries** | N/A | No unique (plan_id, entry_date). Same date can have multiple entries (e.g. two shift types). | By design. |
| **vacation_requests** | `status` + `submitted_at`, `reviewed_at`, `reviewed_by_id`, `rejection_reason` | `uk_vacation_request_employee_year` prevents two requests per employee/year. | Ready for admin (reviewed_* already present). |
| **vacation_request_days** | N/A | `uk_vacation_request_day` prevents duplicate (request, date). FK CASCADE on request delete. | Stable. |

- **Recommendation:** No schema change required for admin. Optional: add a unique constraint or clear policy if you want at most one plan per user per window (e.g. one DRAFT per (user_id, period_start)) to avoid ambiguity.
- **Cleanup:** V6 already removed Sunday vacation_request_days and shift_entries. No further cleanup required for current rules.

---

### 5. UI stability

| Risk | Assessment |
|------|------------|
| **Calendar month jumps** | Holidays: `visibleCalendarDate` stored in `datesSet`; after `refetchEvents` / `loadRequest` we call `changeView(type, toRestore)`. Shifts: `loadWindow` calls `changeView(view.type, view.activeStart)`. Both aim to preserve view; worth testing rapid add/remove and submit. |
| **Stale selected states** | Holidays: Optimistic update then `loadRequest()` on success/failure resyncs from server. Shifts: `loadWindow()` replaces `state.shiftEvents` from API. Good. |
| **Blocked day rendering** | Holidays: `dayCellClassNames` uses `state.request.datesWithShifts` and `state.request.selectedDates`; if request is null or not yet loaded, no blocking. Shift-blocked tooltip set in `datesSet` and after refetch. Shifts: holidays applied in `applyHolidayDayHighlight`; window from API. |
| **Remove/add behavior** | Holidays: Remove via calendar click or event-bar button; both call `removeDay(dateStr)`; API DELETE then `loadRequest()`. Shifts: Delete entry → API DELETE → `loadWindow()`. Consistent. |
| **Responsiveness / layout** | Contract and holidays use responsive CSS (e.g. grid columns, max-width). No obvious layout bugs in reviewed code. |
| **Hidden state bugs** | Holidays: If `loadRequest()` fails, `state.request` stays at previous value; toasts show error. Submit button not wired is the main hidden bug. |

---

## B. Manual Test Checklist

Execute step by step; check off each item.

### Shift planning

- [ ] **Load** Open Select Shifts. Window label shows date range (today + 21 days, 21-day window). Calendar shows only that range or clearly blocks outside dates.
- [ ] **Sundays** Try to add a shift on a Sunday (if in window). Backend must reject with "Shifts cannot be planned on Sundays." UI must not add it or must show error.
- [ ] **Holidays** Try to add a shift on a public holiday in the window. Backend must reject. UI must show error.
- [ ] **Outside window** Try to add a shift before the first allowed date or after the last. Backend must reject. UI must show error.
- [ ] **Add/delete** Add a shift on an allowed weekday. It appears. Delete it (via UI). It disappears. Reload page; state matches.
- [ ] **Save draft** Add several shifts covering a week. Click Save draft. Reload; shifts still there.
- [ ] **Submit** With shifts meeting weekly hours (e.g. 40h for full-time): Submit. Message "submitted for admin approval" and status PENDING. With invalid weekly hours: Submit must be disabled or backend rejects with clear message.
- [ ] **Plan status** After submit, plan status text shows PENDING (or APPROVED/REJECTED when admin exists). Draft can be edited; non-draft cannot (backend rejects add/delete).

### Holiday selection

- [ ] **Load** Open Select Holidays. Entitlement and Selected/Remaining show. If no request yet, list is empty and status can show Draft.
- [ ] **Past dates** Click a past date. Toast "Vacation cannot be in the past." No add.
- [ ] **Sundays** Click a Sunday. Toast "Sundays cannot be selected as vacation days." No add.
- [ ] **Shift conflict** With a shift on a date (from Select Shifts), that date is blocked on holidays calendar. Click it: toast "work shift on this date". No add.
- [ ] **Current year only** If calendar allowed other year (e.g. via URL param), selecting another year date must be rejected (toast "current year").
- [ ] **Add/remove** Select a valid weekday. It appears in list and on calendar (Urlaub). Remove via list or calendar event button. Count and list update. Reload; state matches server.
- [ ] **Exceed entitlement** Select days until count equals entitlement. One more click must show "cannot select more than your entitlement".
- [ ] **Submit button** When selected count equals entitlement, button becomes enabled and helper says "All days selected. You can submit your request."
- [ ] **Submit action** Click "Submit vacation request". **Expected:** Request is submitted (status PENDING), message shown, button disabled. **Current bug:** If no click handler, nothing happens — document this and fix (wire POST /api/vacation-request/submit).
- [ ] **After submit** In DRAFT, add/remove work. After submit (once wired), add/remove must be rejected (toast about DRAFT only).

### Contract overview

- [ ] **Load** Open Contract Overview. Title and intro text visible. Summary shows Contract type, Employment model, Weekly hours, (Monthly hours if Mini-job), Vacation entitlement.
- [ ] **Values** Contract type matches user (e.g. Full-time). Weekly hours and vacation days match what Select Shifts and Select Holidays use (same employee fields).
- [ ] **Planning window** Shift planning card shows "Planning window" with two dates matching the range from Select Shifts (today + delay, + window).
- [ ] **Read-only** No edit controls; footer says contract is managed by organization.

### API and backend

- [ ] **Vacation GET** `GET /api/vacation-request?year=2026` returns 200, body has `entitlementDays`, `selectedCount`, `status`, `selectedDates`, `datesWithShifts`.
- [ ] **Vacation add** `POST /api/vacation-request/days` with `{ "date": "2026-04-15" }` (valid weekday, no shift) returns 201. GET then includes that date.
- [ ] **Vacation remove** `DELETE /api/vacation-request/days/2026-04-15` returns 204. GET no longer includes it.
- [ ] **Vacation submit** `POST /api/vacation-request/submit?year=2026` with exactly entitlement days selected returns 200 and status becomes PENDING. With wrong count returns 400 with message.
- [ ] **Shifts window** `GET /api/shifts/window` returns `allowedStart`, `allowedEnd`, `entries`, `contractInfo`, `holidays`, `planStatus`.

### Database (optional)

- [ ] After submitting vacation: `vacation_requests.status` = 'PENDING', `submitted_at` set.
- [ ] After submitting shifts: `shift_plans.status` = 'PENDING'.
- [ ] No rows in `vacation_request_days` with `DAYOFWEEK(request_date)=1`. No rows in `shift_entries` with `DAYOFWEEK(entry_date)=1`.

---

## C. Remaining Critical Issues

1. **Vacation Submit button not wired (critical)**  
   The "Submit vacation request" button on Select Holidays has no click handler. It never calls `POST /api/vacation-request/submit`.  
   **Fix:** In `holidays.js`, add a click listener on `#btn-submit-vacation` that: checks `state.request.status === 'DRAFT'` and `state.request.selectedCount === state.request.entitlementDays`, then calls `api('POST', '/api/vacation-request/submit?year=' + state.year)`, then on success shows a success toast and calls `loadRequest()`, on error shows `err.data.error` in a toast and calls `loadRequest()`.

2. **No other critical bugs identified**  
   Backend validation, API contracts, DB constraints, and UI state flow are consistent. The only blocking issue for a complete user flow is the missing submit handler.

---

## D. Recommendation for Next Step

- **Do not start admin pages yet.**  
  Fix the **vacation submit button** so the full user flow (select days → submit request) works. After that, run the manual checklist above (at least Holiday selection and Submit action).
- **Then:** Re-run the checklist for Shifts and Contract once, and optionally run the API/DB checks. If all pass, the system is **stable enough to start admin pages**; you can add admin approval for vacation and shift plans on top of the current schema and APIs.
- **Optional hardening:** (1) Add a short comment in `ShiftPlanService` or repository about "at most one DRAFT per user per window" if you want to enforce it later. (2) After wiring submit, add an automated test for "submit vacation request with exact entitlement" (e.g. MockMvc or RestAssured).

**Summary:** Fix the vacation submit handler, run the manual tests, then proceed to admin implementation.
