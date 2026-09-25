package com.medtrack.booking.service;

import com.medtrack.booking.domain.ContactInquiryEntity;
import com.medtrack.booking.repo.ContactInquiryRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactInquiryService {
  private static final int MAX_MESSAGE = 4000;
  private final ContactInquiryRepository repo;

  public ContactInquiryService(ContactInquiryRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public Map<String, Object> submit(Map<String, Object> body) {
    String name = required(body, "name", "fullName");
    String email = required(body, "email");
    String subject = required(body, "subject");
    String message = required(body, "message");
    if (name.length() > 255) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is too long");
    }
    if (!email.contains("@") || email.length() > 255) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
    }
    if (subject.length() > 255) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject is too long");
    }
    if (message.length() < 10) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must be at least 10 characters");
    }
    if (message.length() > MAX_MESSAGE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is too long");
    }

    ContactInquiryEntity row = new ContactInquiryEntity();
    row.setId(UUID.randomUUID().toString());
    row.setFullName(name);
    row.setEmail(email.trim());
    row.setPhone(optional(body, "phone"));
    row.setOrganization(optional(body, "organization"));
    row.setSubject(subject);
    row.setMessage(message);
    row.setStatus("NEW");
    row.setCreatedAt(Instant.now());
    repo.save(row);

    return Map.of(
        "ok",
        true,
        "inquiryId",
        row.getId(),
        "message",
        "Thank you. We received your message and will get back to you.");
  }

  private static String required(Map<String, Object> body, String... keys) {
    for (String key : keys) {
      Object value = body.get(key);
      if (value != null && !String.valueOf(value).trim().isEmpty()) {
        return String.valueOf(value).trim();
      }
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email, subject, and message are required");
  }

  private static String optional(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }
}
