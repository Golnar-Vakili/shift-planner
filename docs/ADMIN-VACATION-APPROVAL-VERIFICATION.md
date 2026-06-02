# Admin Vacation Approval – End-to-End Verification

This document reviews the **Admin Holiday/Vacation Approval** flow, lists missing pieces and edge cases, provides a manual testing checklist, and states whether the flow is stable enough to proceed to **Admin Shift Approval**.

---

## 1. End-to-End Implementation Review

### Employee flow (Holidays page)

| Step | Implementation | Location |
|------|----------------|----------|
| Create vacation request | Employee selects days on calendar (current year, no Sundays, no past, no shift conflicts). Each click calls `POST /api/vacation-request/days` and then `loadRequest()`. | `holidays.js`: `dateClick` → `addDay()` / `removeDay()` |
| Submit request | **Submit** button is enabled when status is DRAFT and selected count equals entitlement. Click calls `POST /api/vacation-request/submit?year=<year>` then shows toast and refreshes via `loadRequest()`. | `holidays.js`: `#btn-submit-vacation` click handler (added in this verification) |
| See status after approve/reject | Status comes from `GET /api/vacation-request?year=`. Employee must refresh or re-open the Holidays page to see PENDING → APPROVED or REJECTED. | `holidays.js`: `renderSummary()`, `loadRequest()` |

- **Backend (employee API):** `VacationRequestController` (`/api/vacation-request`): GET request, POST days, DELETE day, POST submit. All use `@AuthenticationPrincipal EmployeeUserDetails` and pass `employee.getId()`.
- **Submit validation:** `VacationRequestService.submit()` requires DRAFT, own request, selected count exactly equals entitlement, no past dates, no Sundays, current year only.

### Admin flow

| Step | Implementation | Location |
|------|----------------|----------|
| List requests | `GET /admin/vacation-requests` → `AdminVacationRequestService.listForAdmin()`. Sorted: PENDING first, then by `submittedAt` desc. | `AdminVacationRequestController.list()`, `list.html` |
| Open detail | `GET /admin/vacation-requests/{id}` → `getDetailForAdmin(id)`. On invalid id, redirect to list with flash error. | `AdminVacationRequestController.detail()`, `detail.html` |
| Approve | `POST /admin/vacation-requests/{id}/approve` with CSRF. Only PENDING can be approved; sets status APPROVED, `reviewedAt`, `reviewedById`, clears `rejectionReason`. Redirect back to detail with success flash. | `AdminVacationRequestService.approve()`, `detail.html` form |
| Reject | `POST /admin/vacation-requests/{id}/reject` with `reason` (optional, max 500 chars). Only PENDING can be rejected; sets REJECTED, `reviewedAt`, `reviewedById`, `rejectionReason`. Redirect back to detail with success flash. | `AdminVacationRequestService.reject()`, `detail.html` form |

- **Security:** Admin endpoints are under `/admin/**`; assume they are protected by `ROLE_ADMIN` (per project security config). Forms include `_csrf` token.
- **Concurrency:** Approve/reject enforce “only PENDING”; second admin gets a clear error if status already changed.

---

## 2. Missing Pieces, Fragile Points, and Edge Cases

### Fixed in this verification

- **Submit button had no click handler** – Employees could not submit from the UI. A click handler was added on `#btn-submit-vacation` that calls `POST /api/vacation-request/submit?year=...`, shows toast, and calls `loadRequest()`. Without this, the “employee submits request” step was only testable via API/curl.

### Remaining gaps / recommendations

1. **Rejection reason not shown to employee**  
   `VacationRequestDto` does not expose `rejectionReason`. The employee only sees “Your vacation request was rejected. You may edit and resubmit (create a new draft).” Showing the optional reason (e.g. in the status area or a small note) would improve UX.

2. **Resubmit after reject**  
   After reject, the request stays REJECTED. `addDay`/`removeDay` only allow changes in DRAFT. So the employee **cannot** actually “edit and resubmit” for the same year without a way to:
   - reset the existing request to DRAFT (new endpoint or admin action), or  
   - start a new request for the same year (e.g. allow multiple requests per year or “create new draft” that replaces the rejected one).  
   **Recommendation:** Either add a “Reset to draft” (or “Create new draft”) flow and wire it from the Holidays page, or change the UI text to e.g. “Your vacation request was rejected. Contact admin if you need to resubmit.”

3. **Employee sees updated status only after refresh**  
   There is no real-time push. “Employee sees updated status” means: employee refreshes or re-opens the Holidays page. This is acceptable for the current scope but worth noting for manual testing.

4. **List order**  
   List is sorted in memory (PENDING first, then by `submittedAt`). Repository uses `findAllByOrderByUpdatedAtDesc()`; the in-memory sort is correct. No issue.

5. **Double submit**  
   Button is disabled during submit and re-enabled in `finally`; `loadRequest()` then runs and sets button state from new status (PENDING → button disabled). Backend rejects a second submit with “Only a DRAFT request can be submitted.” Safe.

---

## 3. Manual Testing Checklist

Use two users: one **employee**, one **admin**.

### A. Employee creates and submits vacation request

- [ ] Log in as **employee**. Open **Holidays** (or equivalent) page.
- [ ] Ensure current year is used. Select vacation days (no Sundays, no past, within entitlement). Confirm summary shows correct “X of Y days selected” and “Select N more day(s) to submit” or “All days selected. You can submit your request.”
- [ ] When all entitlement days are selected, confirm **Submit vacation request** button is enabled (e.g. highlighted).
- [ ] Click **Submit vacation request**. Confirm success toast (e.g. “Vacation request submitted for approval”) and that status text changes to “Your vacation request has been submitted for approval” and button is disabled.
- [ ] (Optional) Open DevTools → Network; resubmit should not be possible (button disabled). If you force a second POST to submit, backend should return 400 with message about only DRAFT can be submitted.

### B. Admin sees request in list

- [ ] Log in as **admin**. Open **Vacation requests** (or equivalent) in admin menu.
- [ ] Confirm the employee’s request appears in the table (Employee, Year, Days, Status = PENDING, Submitted time, **View** link).
- [ ] Confirm PENDING requests appear before APPROVED/REJECTED if there are others.
- [ ] Confirm any success/error flash from previous actions appears as expected.

### C. Admin opens detail page

- [ ] Click **View** on the PENDING request. Confirm detail page shows: Employee, Year, Contract type, Entitlement, **Selected dates** list, Status = PENDING, Submitted at.
- [ ] Confirm **Approve** and **Reject** (with optional reason) are visible.

### D. Admin approves request

- [ ] On the same detail page, click **Approve**. Confirm redirect back to detail and success message (e.g. “Vacation request approved.”).
- [ ] Confirm status is now APPROVED and Approve/Reject forms are no longer shown.
- [ ] Log in as **employee**, open **Holidays** (refresh if already open). Confirm status text is “Your vacation request has been approved.” and Submit button is not offered.

### E. Admin rejects request (separate test)

- [ ] Use another PENDING request (or re-test with a new submission). Open detail, enter an optional **Rejection reason**, click **Reject**.
- [ ] Confirm redirect and success message; status = REJECTED; **Rejection reason** is shown on detail.
- [ ] Log in as **employee**, open **Holidays** (refresh). Confirm status text is “Your vacation request was rejected. You may edit and resubmit (create a new draft).”
- [ ] (Known gap) Confirm that from the UI the employee currently **cannot** create a new draft for the same year (no “Reset to draft” or “Create new draft” button).

### F. Edge cases (optional)

- [ ] Approve or reject a request that is already APPROVED/REJECTED (e.g. open two tabs, approve in one, then approve again in the other). Confirm second action shows error message.
- [ ] Invalid request id in URL (e.g. `/admin/vacation-requests/99999`). Confirm redirect to list with error message.

---

## 4. Extra Validation and UI Fixes Recommended Before Moving On

- **Done:** Wire **Submit** button so employees can submit from the UI.
- **Recommended (non-blocking):**
  - Expose **rejection reason** to the employee (e.g. add to `VacationRequestDto` and show in status area when status is REJECTED).
  - Either implement **“Reset to draft” / “Create new draft”** after reject and wire it on the Holidays page, or change the copy so it doesn’t promise “edit and resubmit” from the current UI.
- **Optional:** Simple loading state on Submit (e.g. “Submitting…”) to avoid double-clicks; backend already prevents double submit.

No **blocking** validation or UI fixes are required for the checklist above; the flow is testable end-to-end with the submit button fix.

---

## 5. Stability Verdict: Proceed to Admin Shift Approval?

**Yes. The admin vacation approval workflow is stable enough to proceed to Admin Shift Approval.**

- Employee can create and **submit** vacation requests from the UI.
- Admin can list, open detail, approve, and reject with optional reason; CSRF and PENDING-only rules are in place.
- Employee sees updated status after refresh; no critical bugs identified in the approve/reject path.
- Remaining items (rejection reason for employee, resubmit-after-reject flow) are UX improvements and can be done in a follow-up.

Proceeding to **Admin Shift Approval** is reasonable; you can reuse the same patterns (list → detail → approve/reject with optional reason, flash messages, CSRF).
