package com.medtrack.doctor.web;

import com.medtrack.doctor.domain.DoctorRegistrationEntity;
import com.medtrack.doctor.dto.DoctorRegistrationRequest;
import com.medtrack.doctor.service.DoctorRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * REST API for doctor self-registration.
 *
 * <pre>
 * POST /api/doctors/register     — insert registration into DB
 * GET  /api/doctors              — list all registered doctors
 * GET  /api/doctors/{doctorId}   — fetch by business doctor id
 * GET  /api/doctors/health       — health check
 * </pre>
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorRegistrationController {
  private final DoctorRegistrationService service;

  public DoctorRegistrationController(DoctorRegistrationService service) {
    this.service = service;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(@Valid @RequestBody DoctorRegistrationRequest request) {
    DoctorRegistrationEntity saved = service.register(request);
    return Map.of(
        "message", "Doctor registered successfully",
        "doctor", saved);
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "doctor-service");
  }

  @GetMapping
  public Map<String, Object> list() {
    List<DoctorRegistrationEntity> doctors = service.findAll();
    return Map.of("count", doctors.size(), "doctors", doctors);
  }

  @GetMapping("/{doctorId}")
  public Map<String, Object> getByDoctorId(@PathVariable String doctorId) {
    return Map.of("doctor", service.findByDoctorId(doctorId));
  }
}

@Configuration
class DoctorCorsConfig {
  @Bean
  WebMvcConfigurer doctorCors() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*");
      }
    };
  }
}
