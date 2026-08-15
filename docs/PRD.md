# Medical Visit Tracking App — MVP PRD

**Product:** Hospital appointment & visit journey tracker with delay alerts  
**Version:** MVP 1.0  
**Not in scope:** Full EHR, labs, pharmacy, insurance, telemedicine

---

## 1. Goals

- Let call-center/front-desk staff create visits from a phone call in under 45 seconds.
- Track each visit from call → booking → check-in → consult → completion.
- Notify patients on **SMS + Email + Application** for booking, reminders, doctor delay, queue delay, “you’re next,” and timeline status advances.
- Give doctors a minimal queue UI (start / late / complete + short notes).
- Give patients a calm status window: doctor name, fixed appointment time, token, consult-when ETA, and live timeline.

### Success metrics

| Metric | Target |
|--------|--------|
| Staff time to create visit from call | &lt; 45 seconds |
| Delayed visits notified within 2 minutes | ≥ 90% |
| Patient no-show rate vs baseline | Measurable reduction after reminders |
| Call → booked conversion | Tracked per day |

---

## 2. Roles & permissions

| Role | Capabilities |
|------|----------------|
| **Employee (staff)** | Search/create patient by phone; create/update visits; assign doctor & slot; check-in; issue token/slip; advance next token after complete; staff notes; trigger manual notify |
| **Doctor** | View today’s queue; start/end consult; mark running late; add clinical notes; view assigned visit timeline |
| **Patient** | OTP login by phone; patient window (doctor, fixed time, token, consult-when, timeline); receive SMS + Email + App notifications; see patient-visible summary (not full clinical notes) |
| **Admin** | Manage users, departments, doctor schedules, notification templates, grace period, clinic settings |

### Permission matrix (MVP)

| Action | Employee | Doctor | Patient | Admin |
|--------|----------|--------|---------|-------|
| Search patient by phone | Yes | Own patients only | Self | Yes |
| Create / book visit | Yes | No | No | Yes |
| Check-in / issue token | Yes | No | No | Yes |
| Start / complete consult | No | Yes (assigned) | No | Yes |
| Mark doctor late | Yes | Yes | No | Yes |
| Staff notes | Yes | Read | No | Yes |
| Clinical notes | No | Write/read own | Summary only | Yes |
| Manage schedules / users | No | Own schedule view | No | Yes |

---

## 3. Visit status pipeline

```
Called → Booked → CheckedIn → InConsult → Completed
                              ↘ NoShow
                 Cancelled (from Called/Booked/CheckedIn)
```

| Status | Meaning | Typical actor |
|--------|---------|---------------|
| `CALLED` | Phone intake started; not yet booked | Employee |
| `BOOKED` | Doctor + scheduled slot confirmed | Employee |
| `CHECKED_IN` | Patient arrived; token/slip issued | Employee |
| `IN_CONSULT` | Doctor started consultation | Doctor |
| `COMPLETED` | Consult ended | Doctor |
| `NO_SHOW` | Patient did not arrive | Employee / system |
| `CANCELLED` | Visit cancelled | Employee / Patient (request) / Admin |

Every status change writes a `visit_events` timeline row (who, when, from→to, optional note).

---

## 4. Core entities

| Term | Meaning |
|------|---------|
| **Visit / Encounter** | One call or appointment instance |
| **Queue token / Slip** | Doctor slip / token number for the day/doctor |
| **Consult window** | `scheduledStart` → `actualStart` → `actualEnd` |
| **Timeline** | Ordered `visit_events` + notes on that visit |
| **Clinic** | Tenant / branch (`clinicId` on all operational data) |

---

## 5. Notification events (MVP)

Journey events notify on **SMS + Email + Application**. Patient window and message copy stay in sync (doctor, fixed time, token, consult-when).

| Event code | Trigger | Channels (default) |
|------------|---------|-------------------|
| `BOOKING_CONFIRMED` | Visit → `BOOKED` | SMS + Email + App |
| `REMINDER_24H` | 24h before `scheduledStart` | SMS + App |
| `REMINDER_1H` | 1h before `scheduledStart` | SMS + App |
| `CHECKED_IN` | Visit → `CHECKED_IN` (token issued) | SMS + Email + App |
| `DOCTOR_DELAYED` | Manual late or auto after grace | SMS + Email + App |
| `QUEUE_DELAYED` | Current consult overruns → next patient(s) delayed | SMS + Email + App |
| `YOU_ARE_NEXT` | Staff advances next token / position ≤ 2 | SMS + Email + App |
| `CHECKUP_STARTED` | Visit → `IN_CONSULT` | SMS + Email + App |
| `VISIT_COMPLETED` | Visit → `COMPLETED` | SMS + Email + App |

Rules, templates, rate limits: see [NOTIFICATION_RULES.md](./NOTIFICATION_RULES.md).

---

## 6. Screen list (MVP)

### 6.1 Employee (Call center / Front desk)

| Screen | Purpose |
|--------|---------|
| **Login** | Email/password |
| **Home — Quick intake** | Large phone field; search; recent callers |
| **Patient match** | List matches (shared phone / family); confirm name + DOB |
| **Create / edit patient** | Name, phone, DOB/age, gender, email, consent flags |
| **Create visit** | Doctor, department, slot, reason for visit, staff note |
| **Visit detail** | Status, timeline, token, actions (book, check-in, cancel, notify) |
| **Today’s board** | All visits for clinic today; filters by doctor/status |

### 6.2 Doctor

| Screen | Purpose |
|--------|---------|
| **Login** | Email/password |
| **Today’s queue** | Ordered list: current, next 5; delayed badge |
| **Visit consult** | Start / Late (+15) / Complete; clinical note fields |
| **Visit timeline (read)** | Events + own notes |

### 6.3 Patient

| Screen | Purpose |
|--------|---------|
| **OTP login** | Phone + OTP |
| **My visits** | Upcoming + past |
| **Visit status (patient window)** | Doctor name, fixed appointment time, token, consult-when ETA, live status, last update, patient-safe timeline; in-app notifications |
| **Profile / preferences** | Email, SMS / Email / App consent |

### 6.4 Admin

| Screen | Purpose |
|--------|---------|
| **Users** | Create staff/doctors; assign roles |
| **Departments** | Specialty list |
| **Doctor schedules** | Working hours + slots |
| **Notification settings** | Grace period, enabled events, templates |
| **Clinic settings** | Name, timezone, overbooking policy |

---

## 7. Functional requirements (Slice 1+)

### Slice 1 (implemented in this repo)

1. Registration/login for Employee, Doctor, Patient (patient may use password in MVP; OTP-ready).
2. Employee searches patient by phone; creates patient if missing.
3. Employee creates a visit (`CALLED` or `BOOKED`) linked to patient + optional doctor/slot.
4. Visit appears in timeline with initial event.
5. Role-based API access.

### Slice 2 (next)

- Check-in + token issuance  
- Doctor start/end + clinical notes  
- Live queue  

### Slice 3

- Delay job + SMS/email providers  
- Reminders + “you’re next”  

---

## 8. Non-functional requirements

- HTTPS in production; secrets in env vars  
- RBAC on every API  
- Patients only access own visits  
- Audit fields on notes and status changes  
- `clinicId` on operational tables (multi-branch ready)  
- Clinical notes treated as sensitive (separate table, restricted reads)

---

## 9. Out of scope (explicit)

- Billing, insurance, claims  
- Lab / imaging / pharmacy  
- Full EMR charting  
- AI diagnosis  
- WhatsApp (post-MVP)  
- Video consults  

---

## 10. Open configuration (clinic-level defaults)

| Setting | Default |
|---------|---------|
| Late grace period | 10 minutes |
| Max delay alerts per visit | 2 |
| You’re-next threshold | position ≤ 2 |
| Reminder offsets | 24h, 1h |
| Default slot length | 15 minutes |
