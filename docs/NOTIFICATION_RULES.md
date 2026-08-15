# Notification Rules — Delay Job, Templates, Rate Limits

## 1. Channels (always three-way for journey events)

| Channel | Primary use | Consent field |
|---------|-------------|---------------|
| SMS (phone) | Confirmations, reminders, delay, queue updates, you’re next | `patients.sms_consent` |
| Email | Same journey events + status link | `patients.email_consent` |
| Application (in-app / push) | Live patient window + badge; same timeline text as SMS/Email | `patients.push_consent` (default on for logged-in patients) |

**Product rule:** Status changes that affect the patient journey notify on **SMS + Email + Application** (skip only where consent is false). The patient status window must show the same doctor name, fixed appointment time, token, and consult-when as the message copy.

Skip send if consent is false → store notification with `status = SKIPPED`.

---

## 2. Event catalog & defaults

| Event code | When | SMS | Email | App | Rate limit |
|------------|------|-----|-------|-----|------------|
| `BOOKING_CONFIRMED` | Visit becomes `BOOKED` | Yes | Yes | Yes | 1 per visit |
| `REMINDER_24H` | `scheduled_start - 24h` | Yes | Optional | Yes | 1 per visit |
| `REMINDER_1H` | `scheduled_start - 1h` | Yes | Optional | Yes | 1 per visit |
| `CHECKED_IN` | Visit becomes `CHECKED_IN` (token issued) | Yes | Yes | Yes | 1 per visit |
| `DOCTOR_DELAYED` | Manual or auto late (doctor not started / declared late) | Yes | Yes | Yes | Max **2** per visit (`max_delay_alerts_per_visit`) |
| `QUEUE_DELAYED` | Current consult overruns → **next** waiting patient(s) will be delayed | Yes | Yes | Yes | Max **2** per visit (shares delay budget unless staff forces ETA) |
| `YOU_ARE_NEXT` | Staff/doctor advances queue to this token, or position ≤ threshold | Yes | Yes | Yes | 1 per visit (always allowed after status advance) |
| `CHECKUP_STARTED` | Visit becomes `IN_CONSULT` | Yes | Yes | Yes | 1 per visit |
| `VISIT_COMPLETED` | Visit becomes `COMPLETED` | Yes | Yes | Yes | 1 per visit |
| `OVERTIME_FEE` | Complete with overtime charge &gt; 0 | Yes | Yes | Yes | 1 per visit |

Clinic can disable any event via `notification_templates.is_enabled`.

---

## 3. Delay detection job

**Schedule:** every 1 minute (cron / worker).

**Pseudo-logic**

```
for each clinic:
  grace = clinic.late_grace_minutes  # default 10
  maxAlerts = clinic.max_delay_alerts_per_visit  # default 2

  select visits where
    status in (BOOKED, CHECKED_IN)
    and scheduled_start is not null
    and actual_start is null
    and now() > scheduled_start + grace
    and delay_alerts_sent < maxAlerts
    and not cancelled/completed

  for each visit:
    minutesLate = floor((now - scheduled_start) / 60)
    update visits set delay_minutes = minutesLate
    enqueue DOCTOR_DELAYED (SMS + Email)
    increment delay_alerts_sent
    insert visit_events (event_type=DELAY, message=...)
```

**Manual late (doctor/staff):** same enqueue path; typically adds +15 to displayed delay; still respects `max_delay_alerts_per_visit`.

**Second auto alert:** only if still late after another grace window (e.g. another `late_grace_minutes` since last delay notification) — implement by storing `last_delay_notified_at` in visit metadata or checking latest `DOCTOR_DELAYED` notification timestamp.

---

## 4. Consult overrun → next patient delayed

**Trigger:** while a visit is `IN_CONSULT`, if `now > actual_start + expected_consult_minutes + overrun_grace` (clinic defaults: expected 15 min, overrun grace 5 min).

```
current = visit IN_CONSULT for doctor
if now > current.actual_start + expected + overrun_grace:
  nextVisits = next 1..N CHECKED_IN for same doctor (ordered by token)
  for each nextVisit:
    if QUEUE_DELAYED under rate limit:
      recompute consult_when / ETA on visit
      enqueue QUEUE_DELAYED (SMS + Email + App)
      insert visit_events (DELAY / NOTIFY) with patient-safe message
      refresh patient window (realtime)
```

Message intent: “You will be delayed; doctor is still with the previous patient. Updated consult time: {{consult_when}}. Token {{token}}.”

---

## 5. “You’re next” / staff advances next token

**Primary trigger (required):** after doctor **Complete** (or staff marks previous visit completed), staff/system activates the **next token number**. That status change always fires `YOU_ARE_NEXT` with an **exact** timeline message.

**Secondary trigger:** queue position ≤ threshold (default 2), or every minute for active queues as backup.

```
on visit N → COMPLETED:
  next = next CHECKED_IN for doctor by token_number
  if next exists:
    set next status / queue flag to "called" or keep CHECKED_IN with position=1
    update consult_when to "now / please come in"
    enqueue YOU_ARE_NEXT (SMS + Email + App)  # not blocked by delay cap
    insert visit_events STATUS_CHANGE + NOTIFY
    refresh next patient's window: doctor, fixed time, token, consult-when, timeline
```

Backup poll:

```
for each doctor with active queue today:
  ordered = CHECKED_IN visits ordered by token_number / checked_in_at
  for position, visit in enumerate(ordered, start=1):
    if position <= clinic.you_are_next_threshold:
      if no YOU_ARE_NEXT notification exists for visit:
        enqueue YOU_ARE_NEXT (SMS + Email + App)
```

---

## 6. Reminder job

**Schedule:** every 5 minutes.

```
for visits in BOOKED with scheduled_start in windows:
  24h window: scheduled_start between now+23h50m and now+24h10m
  1h window:  scheduled_start between now+50m and now+70m
  if not already sent that event_code → enqueue
```

Do not remind `CANCELLED` / `NO_SHOW` / `COMPLETED`.

---

## 7. Message templates (defaults)

Placeholders: `{{patient_name}}`, `{{doctor_name}}`, `{{clinic_name}}`, `{{scheduled_time}}`, `{{token}}`, `{{delay_minutes}}`, `{{consult_when}}`, `{{status_url}}`

### BOOKING_CONFIRMED — SMS

```
{{clinic_name}}: Hi {{patient_name}}, your visit with {{doctor_name}} is booked for {{scheduled_time}}. Reply HELP for help.
```

### BOOKING_CONFIRMED — Email subject / body

```
Subject: Appointment confirmed — {{scheduled_time}}
Body: Dear {{patient_name}}, your appointment with {{doctor_name}} at {{clinic_name}} is confirmed for {{scheduled_time}}. Track status: {{status_url}}
```

### REMINDER_24H / REMINDER_1H — SMS

```
Reminder: Visit with {{doctor_name}} at {{clinic_name}} on {{scheduled_time}}. Please arrive 10 minutes early.
```

### DOCTOR_DELAYED — SMS

```
{{clinic_name}}: {{doctor_name}} is running about {{delay_minutes}} minutes late. Your visit is still on; we’ll update you. Token {{token}}.
```

### DOCTOR_DELAYED — Email

```
Subject: Update — doctor running late
Body: Dear {{patient_name}}, {{doctor_name}} is delayed by approximately {{delay_minutes}} minutes. Your appointment remains active. Status: {{status_url}}
```

### QUEUE_DELAYED — SMS / Email / App

```
SMS: {{clinic_name}}: You will be delayed. {{doctor_name}} is still with the previous patient. Updated consult time ~{{consult_when}}. Token {{token}}.
Email subject: Update — your visit may be delayed
Email/App body: Dear {{patient_name}}, the current consultation is taking longer than expected. Your fixed appointment was {{scheduled_time}}; updated consult window is ~{{consult_when}}. Track live status: {{status_url}}
```

### YOU_ARE_NEXT — SMS / Email / App

```
SMS: {{clinic_name}}: You’re next (token {{token}}) for {{doctor_name}}. Please come in now / be ready. Consult time: {{consult_when}}.
Email/App: Dear {{patient_name}}, the previous patient is complete. Your token {{token}} is next with {{doctor_name}}. Fixed slot {{scheduled_time}}; please proceed now ({{consult_when}}). Status: {{status_url}}
```

### VISIT_COMPLETED — SMS / Email / App

```
SMS: {{clinic_name}}: Visit with {{doctor_name}} is complete. Thank you. Summary: {{status_url}}
Email subject: Visit completed
Email/App body: Dear {{patient_name}}, your consultation is complete. {{patient_summary}}
```

**App channel:** Persist an in-app notification row and refresh the patient status window (doctor, fixed time, token, consult-when, timeline) so the UI matches SMS/Email without requiring a manual refresh beyond short poll / SSE.

---

## 8. Global rate limits & anti-spam

| Rule | Value |
|------|-------|
| Max delay alerts per visit (`DOCTOR_DELAYED` + `QUEUE_DELAYED`) | 2 |
| Min minutes between delay alerts | = `late_grace_minutes` |
| `YOU_ARE_NEXT` / `CHECKUP_STARTED` / `VISIT_COMPLETED` | Never blocked by delay cap |
| Max SMS per patient per hour | 6 (clinic-wide safety) |
| Max SMS per patient per day | 20 |
| Deduplicate | Same `visit_id` + `event_code` + `channel` → only one SENT/PENDING |

Failed sends: retry up to 3 times with exponential backoff; then `FAILED` + log `error_message`.

---

## 9. Worker implementation notes

- Persist every attempt in `notifications` before calling provider (one row per channel: SMS, Email, App).
- Timeline: insert `visit_events` with `event_type = NOTIFY` when queued/sent; patient window reads the same events.
- On Complete → next token: always enqueue three-channel `YOU_ARE_NEXT` with exact consult-when.
- Dev/MVP: console or file “provider” that logs messages instead of real SMS/email; App channel updates DB for UI.
- Production: MSG91/Twilio (SMS), SES/SendGrid (email), web push / SSE for App; store `provider_ref`.

---

## 10. Configuration API (admin)

```
GET/PATCH /api/admin/notification-settings
{
  "lateGraceMinutes": 10,
  "expectedConsultMinutes": 15,
  "overrunGraceMinutes": 5,
  "maxDelayAlertsPerVisit": 2,
  "youAreNextThreshold": 2,
  "enabledEvents": ["BOOKING_CONFIRMED", "DOCTOR_DELAYED", "QUEUE_DELAYED", "YOU_ARE_NEXT", ...]
}
```
