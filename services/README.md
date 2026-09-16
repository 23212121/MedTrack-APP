# MedTrack Java Microservices

React UI talks to the **gateway** (`:8090`), which routes to:

| Service | Port | Responsibility |
|---------|------|----------------|
| `gateway` | 8090 | CORS + reverse proxy (avoids Apache on :8080) |
| `schedule-service` | 8081 | Weekly schedules, availability, busy chart, overtime fee rules |
| `visit-service` | 8082 | Visits, check-in/token, start/late/complete, fee apply |
| `notification-service` | 8083 | SMS (phone) + email for checkup lifecycle events |
| `booking-service` | 8084 | REST GET/POST appointments — fetch from & insert into H2 DB |
| `doctor-service` | 8085 | Doctor registration |
| `hrm-service` | 8086 | HRM attendance / leave / inbox |
| `medtrack-app` | 8090 | Unified jar (embeds most modules) — **recommended** |

```
web (Vite :5173) → gateway / medtrack-app :8090 → schedule / visit / notification / booking
```

## Service status (Spring Boot Actuator)

| URL | Purpose |
|-----|---------|
| `GET /actuator/health` | Local health (DB, disk, etc.) |
| `GET /actuator/info` | App info |
| `GET /api/system/status` | Aggregated status of all configured services |
| UI `/system-status` | Auto-refresh dashboard |

When using **medtrack-app**, modules marked `embedded: true` show as **EMBEDDED** (running inside :8090). Separate processes on 8081–8086 show **UP** when their own Actuator responds.
## Prerequisites

- JDK 17+
- Maven 3.9+
- Node 20+ (for `../web`)

## Run services

From `services/`:

```bash
mvn -pl common,schedule-service,visit-service,notification-service,booking-service,gateway -am package -DskipTests
```

Then in separate terminals:

```bash
mvn -pl notification-service spring-boot:run
mvn -pl schedule-service spring-boot:run
mvn -pl visit-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl gateway spring-boot:run
```

### Booking REST API (GET + POST)

See [`booking-service/README.md`](booking-service/README.md).

```bash
# Insert
curl -X POST http://localhost:8084/api/bookings -H "Content-Type: application/json" -d "{\"doctorId\":\"d1\",\"doctorName\":\"Dr. Amit\",\"patientName\":\"Rahul\",\"patientPhone\":\"9123456780\",\"appointmentDate\":\"2026-07-20\",\"appointmentTime\":\"2026-07-20T13:30:00Z\"}"

# Fetch all / by doctor
curl http://localhost:8084/api/bookings
curl "http://localhost:8084/api/bookings?doctorId=d1"
```

Windows PowerShell helper:

```powershell
.\start-all.ps1
```

## Notification events (SMS + Email)

| Event | When |
|-------|------|
| `BOOKING_CONFIRMED` | Visit booked |
| `CHECKED_IN` | Front-desk check-in + token |
| `CHECKUP_STARTED` | Doctor starts consult |
| `DOCTOR_DELAYED` | Manual +15 late |
| `YOU_ARE_NEXT` | Queue position ≤ 2 |
| `VISIT_COMPLETED` | Consult complete |
| `OVERTIME_FEE` | Extra charge after fixed consult minutes |

MVP providers log SMS/email to the console. Point `spring.mail.*` at MailHog/SES for real email; swap SMS provider in `NotificationAppService`.

## Seed IDs

- Doctor: `seed-doctor-1` (Dr. Mehta)
- Patient: Anita Sharma / `9876543210`
- Clinic: `seed-clinic-1`
