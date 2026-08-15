# Data model

- **PostgreSQL reference DDL:** [schema.sql](./schema.sql)
- **Runtime ORM (MVP local SQLite):** [../prisma/schema.prisma](../prisma/schema.prisma)

Core tables: `clinics`, `users`, `departments`, `doctors`, `doctor_schedules`, `schedule_slots`, `doctor_availability`, `patients`, `visits`, `visit_events`, `visit_notes`, `visit_fee_charges`, `notification_templates`, `notifications`, `otp_challenges`.

Slice 2 extras (fees + availability): [schema-slice2.sql](./schema-slice2.sql).

See PRD entity definitions in [PRD.md](./PRD.md).

### Fee fields

| Field | Meaning |
|-------|---------|
| `clinics.fixed_consult_minutes` | Included consult duration |
| `clinics.overtime_fee_amount` | Charge per overtime block |
| `visits.overtime_minutes` / `overtime_fee` / `total_fee` | Applied at complete |
| `visit_fee_charges` | Line items (BASE / OVERTIME) |
