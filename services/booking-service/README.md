# Booking Service (Spring Boot REST API)

Java Spring Boot service that **fetches** and **inserts** appointment bookings in an H2 database.

| Method | URL | Action |
|--------|-----|--------|
| `GET` | `/api/bookings` | Fetch all bookings |
| `GET` | `/api/bookings/{id}` | Fetch one booking |
| `GET` | `/api/bookings?doctorId=...` | Fetch bookings for a doctor (desk) |
| `GET` | `/api/bookings?doctorId=...&date=2026-07-20` | Fetch doctor's bookings for a date |
| `GET` | `/api/bookings?phone=9876543210` | Fetch by patient phone |
| `POST` | `/api/bookings` | **Insert** a booking into the DB |
| `GET` | `/api/bookings/health` | Health check |

- Port: **8084**
- DB: H2 file `./data/bookings` (H2 console: http://localhost:8084/h2-console)
- JDBC URL: `jdbc:h2:file:./data/bookings`
- User / password: `sa` / *(empty)*

## Run (needs JDK 17+)

```bash
cd services
mvn -pl booking-service -am spring-boot:run
```

## POST — insert into database

```bash
curl -X POST http://localhost:8084/api/bookings ^
  -H "Content-Type: application/json" ^
  -d "{\"clinicId\":\"seed-clinic-2\",\"clinicName\":\"Apollo Daycare Hospital\",\"doctorId\":\"seed-doctor-apollo\",\"doctorName\":\"Dr. Amit Sharma\",\"patientName\":\"Rahul Kumar\",\"patientPhone\":\"9123456780\",\"patientAge\":32,\"gender\":\"MALE\",\"address\":\"Andheri\",\"reason\":\"Fever\",\"appointmentDate\":\"2026-07-20\",\"appointmentTime\":\"2026-07-20T13:30:00Z\",\"consultationFee\":500,\"currency\":\"INR\"}"
```

## GET — fetch from database

```bash
# All bookings
curl http://localhost:8084/api/bookings

# One booking
curl http://localhost:8084/api/bookings/{id}

# Same doctor desk view
curl "http://localhost:8084/api/bookings?doctorId=seed-doctor-apollo"
```

Via gateway: `http://localhost:8090/api/bookings`
