# MedTrack Clinic — Medical Visit Tracking

Hospital appointment & visit journey tracker with **doctor schedules**, **check-in**, **busy chart**, **overtime fees**, and **SMS/email checkup notifications**.

## Architecture

| Layer | Stack | Path |
|-------|-------|------|
| **React SPA** (primary UI) | Vite + React Router | [`web/`](web/) |
| **Java API** (single port) | Spring Boot 3 | [`services/medtrack-app`](services/medtrack-app) |
| Next.js MVP (Slice 1+) | Next.js + Prisma SQLite | `src/`, `prisma/` |
| Docs | PRD / schema / UX / notify rules | [`docs/`](docs/) |

```
React :5173 ──► medtrack-app :8090
                  (schedules, visits, notifications, bookings — one process)
```

## Quick start — React + Java (recommended)

### 1. Start the API (single port)

```powershell
cd medical-visit-tracker\services
.\start-all.ps1
```

Or manually (JDK 17 + Maven):

```bash
cd medical-visit-tracker/services
mvn -pl medtrack-app -am package -DskipTests
java -jar medtrack-app/target/medtrack-app-1.0.0-SNAPSHOT.jar
```

API: [http://localhost:8090/api/health](http://localhost:8090/api/health)

### 2. Start React UI

```bash
cd medical-visit-tracker/web
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

### React pages

| Page | Route | Purpose |
|------|-------|---------|
| Schedules | `/schedules` | Weekly `doctor_schedules` |
| Availability | `/availability` | Busy / leave / blocked blocks |
| Check-in | `/check-in` | Token + SMS/email |
| Busy chart | `/chart` | Working / booked / busy utilization |
| Fees | `/fees` | Fixed consult time + overtime charge |
| Doctor queue | `/doctor` | Start / late / complete |
| Notifications | `/notifications` | Mail & phone history |

## PostgreSQL (local)

PostgreSQL 18 is expected on `localhost:5432`. Create the MedTrack DB/user and wire `.env`:

```powershell
cd medical-visit-tracker
.\scripts\setup-postgres.ps1 -PostgresAdminPassword 'YOUR_POSTGRES_PASSWORD'
npx prisma db push
npm run db:seed
```

Defaults created by the script:

| Setting | Value |
|---------|-------|
| Database | `medical_visit_tracker` |
| User / password | `medtrack` / `medtrack` |
| Prisma URL | in `.env` as `DATABASE_URL` |
| Spring JDBC | `SPRING_DATASOURCE_*` in `.env` |

## Next.js local MVP (optional)

```bash
cd medical-visit-tracker
npm install
npm run db:setup
npm run dev
```

Seed logins (password `password123`): `employee@clinic.local`, `doctor@clinic.local`, `admin@clinic.local`, `patient@clinic.local`.

## Notifications (checkup changes)

Both stacks send **SMS (phone)** + **email** on:

- Booking confirmed  
- Checked in  
- Checkup started  
- Doctor delayed  
- You’re next  
- Visit completed  
- Overtime fee after fixed consult minutes  

See [docs/NOTIFICATION_RULES.md](docs/NOTIFICATION_RULES.md) and schema extras in [docs/schema-slice2.sql](docs/schema-slice2.sql).

## Fee rule (default)

- Base consult: **INR 500** for **15 minutes**
- Extra: **INR 200** per **15-minute** block after the fixed time
- Applied on visit **complete**; patient notified via SMS + email
