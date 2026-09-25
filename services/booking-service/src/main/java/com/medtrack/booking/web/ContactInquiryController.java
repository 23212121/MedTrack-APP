package com.medtrack.booking.web;

import com.medtrack.booking.service.ContactInquiryService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
public class ContactInquiryController {
  private final ContactInquiryService service;

  public ContactInquiryController(ContactInquiryService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
    return service.submit(body);
  }
}
