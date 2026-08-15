package com.medtrack.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Enumeration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootApplication
@RestController
public class GatewayApplication {
  private final RestTemplate rest = new RestTemplate();

  @Value("${medtrack.schedule-url}") private String scheduleUrl;
  @Value("${medtrack.visit-url}") private String visitUrl;
  @Value("${medtrack.notify-url}") private String notifyUrl;
  @Value("${medtrack.booking-url}") private String bookingUrl;

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }

  @RequestMapping("/api/schedules/**")
  public ResponseEntity<byte[]> schedules(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(scheduleUrl, request, body);
  }

  @RequestMapping("/api/visits/**")
  public ResponseEntity<byte[]> visits(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(visitUrl, request, body);
  }

  @RequestMapping(value = "/api/visits", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> visitsRoot(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(visitUrl, request, body);
  }

  @RequestMapping("/api/notifications/**")
  public ResponseEntity<byte[]> notifications(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(notifyUrl, request, body);
  }

  @RequestMapping(value = "/api/notifications", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> notificationsRoot(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(notifyUrl, request, body);
  }

  @RequestMapping("/api/bookings/**")
  public ResponseEntity<byte[]> bookings(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping(value = "/api/bookings", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> bookingsRoot(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping("/api/doctors/**")
  public ResponseEntity<byte[]> doctors(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping(value = "/api/doctors", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> doctorsRoot(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping("/api/departments/**")
  public ResponseEntity<byte[]> departments(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping(
      value = "/api/departments",
      method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
  public ResponseEntity<byte[]> departmentsRoot(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping("/api/hospitals/**")
  public ResponseEntity<byte[]> hospitals(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping(value = "/api/hospitals", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> hospitalsRoot(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping("/api/auth/**")
  public ResponseEntity<byte[]> auth(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @RequestMapping(value = "/api/auth", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> authRoot(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    return proxy(bookingUrl, request, body);
  }

  @GetMapping("/api/health")
  public MapHealth health() {
    return new MapHealth("UP", "gateway");
  }

  public record MapHealth(String status, String service) {}

  private ResponseEntity<byte[]> proxy(String base, HttpServletRequest request, byte[] body) {
    String path = request.getRequestURI();
    String query = request.getQueryString();
    URI uri = UriComponentsBuilder.fromHttpUrl(base + path)
        .query(query)
        .build(true)
        .toUri();

    HttpHeaders headers = new HttpHeaders();
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length")) continue;
      headers.add(name, request.getHeader(name));
    }
    HttpMethod method = HttpMethod.valueOf(request.getMethod());
    HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
    try {
      ResponseEntity<byte[]> resp = rest.exchange(uri, method, entity, byte[].class);
      HttpHeaders out = new HttpHeaders();
      MediaType ct = resp.getHeaders().getContentType();
      if (ct != null) out.setContentType(ct);
      return new ResponseEntity<>(resp.getBody(), out, resp.getStatusCode());
    } catch (HttpStatusCodeException ex) {
      return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsByteArray());
    }
  }

  @Bean
  WebMvcConfigurer gatewayCors() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*");
      }
    };
  }
}
