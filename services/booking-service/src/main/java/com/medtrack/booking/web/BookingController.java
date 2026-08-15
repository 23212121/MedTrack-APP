package com.medtrack.booking.web;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.dto.BookingContext;
import com.medtrack.booking.dto.CreateBookingRequest;
import com.medtrack.booking.dto.RescheduleBookingRequest;
import com.medtrack.booking.service.AppointmentAppService;
import com.medtrack.booking.service.AppointmentEnrichmentService;
import com.medtrack.booking.service.BookingAppService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
  private final BookingAppService service;
  private final AppointmentAppService appointmentService;
  private final AppointmentEnrichmentService enrichment;

  public BookingController(
      BookingAppService service,
      AppointmentAppService appointmentService,
      AppointmentEnrichmentService enrichment) {
    this.service = service;
    this.appointmentService = appointmentService;
    this.enrichment = enrichment;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(value = "doctorId", required = false) String doctorId,
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestParam(value = "clinicId", required = false) String clinicId,
      @RequestParam(value = "phone", required = false) String phone,
      @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    List<BookingEntity> bookings;
    if (doctorId != null && !doctorId.isBlank()) {
      bookings = service.findByDoctor(doctorId, date);
    } else if (hospitalId != null) {
      bookings = service.findByHospital(hospitalId);
    } else if (clinicId != null && !clinicId.isBlank()) {
      try {
        bookings = service.findByHospital(Long.parseLong(clinicId.trim()));
      } catch (NumberFormatException ex) {
        bookings = List.of();
      }
    } else if (phone != null && !phone.isBlank()) {
      bookings = service.findByPhone(phone);
    } else {
      bookings = service.findAll();
    }
    List<Map<String, Object>> rows = bookings.stream().map(enrichment::enrichBooking).toList();
    return Map.of("count", rows.size(), "bookings", rows);
  }

  @GetMapping("/{id}")
  public Map<String, Object> getById(@PathVariable("id") String id) {
    return Map.of("booking", enrichment.enrichBooking(service.findById(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @Valid @RequestBody CreateBookingRequest request,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    BookingContext ctx = BookingContext.fromRequest(request, hospitalHeader, userHeader);
    BookingEntity saved = service.create(request, ctx);
    AppointmentEntity appointment =
        appointmentService.findByBookingRef(saved.getId()).orElse(null);
    if (appointment != null) {
      return Map.of(
          "message", "Appointment saved to database",
          "booking", enrichment.enrichBooking(saved),
          "appointment", enrichment.enrichAppointment(appointment));
    }
    return Map.of(
        "message", "Appointment saved to database",
        "booking", enrichment.enrichBooking(saved));
  }

  @PostMapping("/{id}/cancel")
  public Map<String, Object> cancel(
      @PathVariable("id") String id, @RequestParam("phone") String phone) {
    BookingEntity saved = service.cancel(id, phone);
    return Map.of("message", "Booking cancelled", "booking", enrichment.enrichBooking(saved));
  }

  @PatchMapping("/{id}")
  public Map<String, Object> reschedule(
      @PathVariable("id") String id,
      @RequestParam("phone") String phone,
      @Valid @RequestBody RescheduleBookingRequest request) {
    BookingEntity saved = service.reschedule(id, phone, request);
    return Map.of("message", "Booking rescheduled", "booking", enrichment.enrichBooking(saved));
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "booking-service");
  }
}
