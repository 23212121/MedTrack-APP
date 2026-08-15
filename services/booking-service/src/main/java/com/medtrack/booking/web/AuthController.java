package com.medtrack.booking.web;

import com.medtrack.booking.service.AuthService;
import com.medtrack.booking.service.UserRegistrationService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;
  private final UserRegistrationService userRegistrationService;

  public AuthController(AuthService service, UserRegistrationService userRegistrationService) {
    this.service = service;
    this.userRegistrationService = userRegistrationService;
  }

  /**
   * Choose login mode:
   * HOSPITAL — id = hospital ID, password = hospital admin password
   * USER — id = doctor ID or email, password = doctor login password
   */
  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody Map<String, Object> body) {
    return service.login(body);
  }

  /** Patient portal registration — phone + password + name. */
  @PostMapping("/register-patient")
  public Map<String, Object> registerPatient(@RequestBody Map<String, Object> body) {
    return service.registerPatient(body);
  }

  /** Next auto-generated User ID (backend use only — not shown on the registration form). */
  @GetMapping("/next-user-id")
  public Map<String, Object> nextUserId() {
    return userRegistrationService.nextUserId();
  }

  /**
   * Patient/User Registration — inserts into svc.user_details.
   * Client sends userName, phone, password; user_id is generated server-side.
   */
  @PostMapping("/register-user")
  public Map<String, Object> registerUser(@RequestBody Map<String, Object> body) {
    return userRegistrationService.register(body);
  }
}
