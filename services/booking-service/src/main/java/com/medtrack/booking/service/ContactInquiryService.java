package com.medtrack.booking.service;

import com.medtrack.booking.domain.ContactInquiryEntity;
import com.medtrack.booking.repo.ContactInquiryRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactInquiryService {
  private static final Logger log = LoggerFactory.getLogger(ContactInquiryService.class);
  private static final int MAX_MESSAGE = 4000;
  private final ContactInquiryRepository repo;
  private final ObjectProvider<JavaMailSender> mailSender;

  @Value("${medtrack.email-from:azherkhan061@gmail.com}")
  private String emailFrom;

  @Value("${medtrack.contact-to:azherkhan061@gmail.com}")
  private String contactTo;

  public ContactInquiryService(
      ContactInquiryRepository repo, ObjectProvider<JavaMailSender> mailSender) {
    this.repo = repo;
    this.mailSender = mailSender;
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

    String phone = optional(body, "phone");
    String organization = optional(body, "organization");

    ContactInquiryEntity row = new ContactInquiryEntity();
    row.setId(UUID.randomUUID().toString());
    row.setFullName(name);
    row.setEmail(email.trim());
    row.setPhone(phone);
    row.setOrganization(organization);
    row.setSubject(subject);
    row.setMessage(message);
    row.setStatus("NEW");
    row.setCreatedAt(Instant.now());
    repo.save(row);

    boolean emailed = forwardToInbox(row);
    row.setStatus(emailed ? "EMAILED" : "EMAIL_FAILED");
    repo.save(row);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("inquiryId", row.getId());
    out.put("emailed", emailed);
    out.put(
        "message",
        emailed
            ? "Thank you. We received your message and will get back to you."
            : "Thank you. We saved your message. Email delivery is not configured on the server yet.");
    return out;
  }

  private boolean forwardToInbox(ContactInquiryEntity row) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn("Contact inquiry {} saved but SMTP is unavailable", row.getId());
      return false;
    }
    String from = blankTo(emailFrom, "azherkhan061@gmail.com");
    String to = blankTo(contactTo, from);
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setFrom(from);
      msg.setTo(to);
      msg.setReplyTo(row.getEmail());
      msg.setSubject("MedTrack inquiry: " + row.getSubject());
      msg.setText(buildBody(row));
      sender.send(msg);
      log.info("Contact inquiry {} emailed from={} to={}", row.getId(), from, to);
      return true;
    } catch (Exception ex) {
      log.warn(
          "Contact inquiry {} saved but SMTP send failed from={} to={}: {} — set MAIL_USER and MAIL_PASS (Gmail App Password)",
          row.getId(),
          from,
          to,
          ex.getMessage());
      return false;
    }
  }

  private static String buildBody(ContactInquiryEntity row) {
    return """
        New message from the MedTrack Send-message form.

        Name: %s
        Email: %s
        Phone: %s
        Hospital / organization: %s
        Subject: %s

        Message:
        %s

        Inquiry ID: %s
        Sent at: %s
        """
        .formatted(
            nullTo(row.getFullName(), "—"),
            nullTo(row.getEmail(), "—"),
            nullTo(row.getPhone(), "—"),
            nullTo(row.getOrganization(), "—"),
            nullTo(row.getSubject(), "—"),
            nullTo(row.getMessage(), "—"),
            row.getId(),
            row.getCreatedAt());
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

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String nullTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
