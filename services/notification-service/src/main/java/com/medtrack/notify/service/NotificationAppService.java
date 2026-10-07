package com.medtrack.notify.service;

import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.notify.domain.NotificationEntity;
import com.medtrack.notify.repo.NotificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationAppService {
  private static final Logger log = LoggerFactory.getLogger(NotificationAppService.class);

  private final NotificationRepository repo;
  private final ObjectProvider<JavaMailSender> mailSender;

  @Value("${medtrack.email-from}")
  private String emailFrom;
  @Value("${medtrack.notify-to:azherkhan061@gmail.com}")
  private String notifyTo;
  @Value("${medtrack.console-fallback:true}")
  private boolean consoleFallback;

  public NotificationAppService(
      NotificationRepository repo, ObjectProvider<JavaMailSender> mailSender) {
    this.repo = repo;
    this.mailSender = mailSender;
  }

  @Transactional
  public Map<String, Object> send(NotifyRequest req) {
    List<NotificationEntity> created = new ArrayList<>();

    if (req.patientPhone() != null && !req.patientPhone().isBlank()) {
      created.add(sendChannel(req, "SMS", req.patientPhone(), req.smsConsent()));
    }
    String patientEmail = req.patientEmail() == null ? "" : req.patientEmail().trim();
    String companyInbox = notifyTo == null ? "" : notifyTo.trim();
    if (!patientEmail.isBlank()) {
      created.add(sendChannel(req, "EMAIL", patientEmail, req.emailConsent() || !companyInbox.isBlank()));
    } else if (!companyInbox.isBlank()) {
      created.add(sendChannel(req, "EMAIL", companyInbox, true));
    }
    if (req.patientPhone() != null && !req.patientPhone().isBlank()) {
      created.add(sendChannel(req, "APP", req.patientPhone(), true));
    }
    return Map.of("notifications", created, "eventCode", req.eventCode());
  }

  private NotificationEntity sendChannel(
      NotifyRequest req, String channel, String recipient, boolean consent) {
    if (!Set.of("DOCUMENT_ATTACHED", "MEDICINE_ORDER", "HRM_APPROVAL", "PAYMENT_COMPLETED")
            .contains(req.eventCode() == null ? "" : req.eventCode())
        && repo.existsByVisitIdAndEventCodeAndChannelAndStatusIn(
            req.visitId(), req.eventCode(), channel, List.of("SENT", "PENDING"))) {
      NotificationEntity skip = base(req, channel, recipient);
      skip.setStatus("SKIPPED");
      skip.setErrorMessage("Duplicate event+channel for visit");
      return repo.save(skip);
    }

    String subject = subjectFor(req);
    String body = bodyFor(req, channel);
    NotificationEntity n = base(req, channel, recipient);
    n.setSubject(subject);
    n.setBody(body);

    if (!consent) {
      n.setStatus("SKIPPED");
      n.setErrorMessage("No consent");
      return repo.save(n);
    }

    try {
      if ("SMS".equals(channel)) {
        log.info("[SMS] to={} event={} :: {}", recipient, req.eventCode(), body);
        n.setProviderRef("console-sms");
        n.setStatus("SENT");
        n.setSentAt(Instant.now());
      } else if ("APP".equals(channel)) {
        log.info("[APP] to={} event={} :: {}", recipient, req.eventCode(), body);
        n.setProviderRef("in-app");
        n.setStatus("SENT");
        n.setSentAt(Instant.now());
      } else {
        boolean emailed = tryEmail(recipient, subject, body);
        if (emailed) {
          n.setProviderRef("smtp");
          n.setStatus("SENT");
          n.setSentAt(Instant.now());
        } else if (consoleFallback) {
          log.info("[EMAIL-fallback] to={} event={} :: {}", recipient, req.eventCode(), body);
          n.setProviderRef("console-email");
          n.setStatus("SENT");
          n.setSentAt(Instant.now());
        } else {
          n.setStatus("FAILED");
          n.setErrorMessage("SMTP unavailable");
        }
      }
    } catch (Exception ex) {
      n.setStatus("FAILED");
      n.setErrorMessage(ex.getMessage());
    }
    return repo.save(n);
  }

  private boolean tryEmail(String to, String subject, String body) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn("SMTP unavailable — JavaMailSender bean missing (check spring.mail.* / MAIL_*)");
      return false;
    }
    if (emailFrom == null || emailFrom.isBlank() || emailFrom.endsWith(".local")) {
      log.warn(
          "SMTP from-address not configured for real delivery (medtrack.email-from / MAIL_FROM). from={}",
          emailFrom);
    }
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setFrom(emailFrom);
      msg.setTo(to);
      String bcc = notifyTo == null ? "" : notifyTo.trim();
      if (!bcc.isBlank() && !bcc.equalsIgnoreCase(to)) {
        msg.setBcc(bcc);
      }
      msg.setSubject(subject);
      msg.setText(body);
      sender.send(msg);
      log.info("[EMAIL] sent to={} bcc={} from={} event-subject={}", to, bcc, emailFrom, subject);
      return true;
    } catch (Exception ex) {
      log.warn(
          "SMTP send failed to={} from={}: {} — set MAIL_USER + MAIL_PASS (Gmail App Password)",
          to,
          emailFrom,
          ex.getMessage());
      return false;
    }
  }

  private NotificationEntity base(NotifyRequest req, String channel, String recipient) {
    NotificationEntity n = new NotificationEntity();
    n.setClinicId(req.clinicId());
    n.setHospitalId(resolveHospitalId(req.clinicId()));
    n.setVisitId(req.visitId());
    n.setPatientId(req.patientId());
    n.setEventCode(req.eventCode());
    n.setChannel(channel);
    n.setRecipient(recipient);
    return n;
  }

  private String subjectFor(NotifyRequest req) {
    return switch (req.eventCode()) {
      case "BOOKING_CONFIRMED" -> "Your appointment has been booked — " + nullTo(req.clinicName(), "MedTrack Clinic");
      case "PAYMENT_COMPLETED" -> "Payment successful — " + nullTo(req.clinicName(), "MedTrack Clinic");
      case "DOCUMENT_ATTACHED" -> "Document attached to your appointment — " + nullTo(req.clinicName(), "MedTrack Clinic");
      case "CHECKED_IN" -> "Checked in — token " + req.token();
      case "CHECKUP_STARTED" -> "Your checkup has started";
      case "DOCTOR_DELAYED" -> "Update — doctor running late";
      case "YOU_ARE_NEXT" -> "You're next — token " + req.token();
      case "QUEUE_TWO_AHEAD" -> "Almost your turn — token " + req.token();
      case "VISIT_COMPLETED" -> "Visit completed";
      case "OVERTIME_FEE" -> "Extra consult fee applied";
      case "TRACK_OTP" -> "MedTrack appointment OTP";
      case "MEDICINE_ORDER" -> "Medicine order update — " + nullTo(req.clinicName(), "MedTrack");
      case "HRM_APPROVAL" -> "HR update — " + nullTo(req.clinicName(), "MedTrack");
      default -> "Clinic update — " + req.eventCode();
    };
  }

  private String bodyFor(NotifyRequest req, String channel) {
    String clinic = nullTo(req.clinicName(), "Clinic");
    String patient = nullTo(req.patientName(), "Patient");
    String doctor = nullTo(req.doctorName(), "your doctor");
    String fee = req.totalFee() == null
        ? ""
        : (req.feeCurrency() == null ? "INR" : req.feeCurrency()) + " " + req.totalFee();
    String ot = req.overtimeFee() == null ? "0" : String.valueOf(req.overtimeFee());
    String tokenPart =
        req.token() == null || req.token().isBlank() || "—".equals(req.token())
            ? ""
            : " Token # " + req.token() + ".";

    return switch (req.eventCode()) {
      case "BOOKING_CONFIRMED" -> {
        String base =
            clinic
                + ": Hi "
                + patient
                + ", your appointment has been booked with "
                + doctor
                + " for "
                + req.scheduledTime()
                + "."
                + tokenPart;
        if ("EMAIL".equals(channel)) {
          yield "Dear "
              + patient
              + ",\n\nYour appointment has been booked.\n\n"
              + "Hospital/Clinic: "
              + clinic
              + "\nDoctor: "
              + doctor
              + "\nWhen: "
              + req.scheduledTime()
              + "\nToken: "
              + (req.token() == null || req.token().isBlank() ? "—" : "# " + req.token())
              + "\n\nPlease arrive on time. Track your visit with your mobile number on the patient track page.\n\nRegards,\n"
              + clinic;
        }
        yield base;
      }
      case "PAYMENT_COMPLETED" -> {
        String txn =
            req.extra() != null && req.extra().get("transactionId") != null
                ? req.extra().get("transactionId")
                : "—";
        String apptId =
            req.extra() != null && req.extra().get("appointmentId") != null
                ? req.extra().get("appointmentId")
                : req.visitId();
        String paid =
            req.totalFee() == null
                ? ""
                : (req.feeCurrency() == null ? "INR" : req.feeCurrency()) + " " + req.totalFee();
        if ("EMAIL".equals(channel)) {
          yield "Dear "
              + patient
              + ",\n\nPayment successful!\n\n"
              + "Appointment: "
              + apptId
              + "\nDoctor: "
              + doctor
              + "\nAmount Paid: "
              + paid
              + "\nTransaction ID: "
              + txn
              + "\n\nRegards,\n"
              + clinic;
        }
        yield clinic
            + ": Payment successful! Appointment "
            + apptId
            + " with "
            + doctor
            + ". Amount paid "
            + paid
            + ". Txn "
            + txn
            + ".";
      }
      case "DOCUMENT_ATTACHED" -> {
        String count =
            req.extra() != null && req.extra().get("fileCount") != null
                ? req.extra().get("fileCount")
                : "1";
        String names =
            req.extra() != null && req.extra().get("fileNames") != null
                ? req.extra().get("fileNames")
                : "";
        String filesBit = names.isBlank() ? count + " file(s)" : names;
        if ("EMAIL".equals(channel)) {
          yield "Dear "
              + patient
              + ",\n\nA document has been attached to your appointment.\n\n"
              + "Hospital/Clinic: "
              + clinic
              + "\nDoctor: "
              + doctor
              + "\nWhen: "
              + nullTo(req.scheduledTime(), "—")
              + "\nToken: "
              + (req.token() == null || req.token().isBlank() ? "—" : "# " + req.token())
              + "\nFiles: "
              + filesBit
              + "\n\nOpen Reports in the patient portal to view or download.\n\nRegards,\n"
              + clinic;
        }
        yield clinic
            + ": Hi "
            + patient
            + ", "
            + filesBit
            + " attached to your appointment with "
            + doctor
            + "."
            + tokenPart
            + " Open Reports to view.";
      }
      case "CHECKED_IN" -> clinic + ": " + patient + " checked in. Token " + req.token()
          + " for " + doctor + ".";
      case "CHECKUP_STARTED" -> clinic + ": Your checkup with " + doctor + " has started. Token "
          + req.token() + ".";
      case "DOCTOR_DELAYED" -> clinic + ": " + doctor + " is running about "
          + req.delayMinutes() + " minutes late. Token " + req.token() + ".";
      case "YOU_ARE_NEXT" -> clinic + ": You're next (token " + req.token() + ") for " + doctor
          + ". Please be ready.";
      case "QUEUE_TWO_AHEAD" -> clinic + ": Only 2 patients ahead of you (token " + req.token()
          + ") for " + doctor + ". Please be ready soon.";
      case "VISIT_COMPLETED" -> clinic + ": Visit with " + doctor + " is complete. Total fee: "
          + fee + ".";
      case "OVERTIME_FEE" -> clinic + ": Consult exceeded fixed time. Extra charge " + ot
          + " applied. Total " + fee + ". " + ("EMAIL".equals(channel)
          ? "Reply to this email with questions."
          : "Call clinic for fee questions.");
      case "TRACK_OTP" -> {
        String otp =
            req.extra() != null && req.extra().get("otp") != null ? req.extra().get("otp") : "";
        yield clinic
            + ": Your OTP to track the appointment is "
            + otp
            + ". Valid for 5 minutes. Do not share this code.";
      }
      case "MEDICINE_ORDER" -> {
        String extraMsg =
            req.extra() != null && req.extra().get("message") != null
                ? req.extra().get("message")
                : "Your medicine order was updated.";
        String orderNo =
            req.extra() != null && req.extra().get("orderNumber") != null
                ? req.extra().get("orderNumber")
                : "";
        if ("EMAIL".equals(channel)) {
          yield "Dear "
              + patient
              + ",\n\n"
              + extraMsg
              + "\n\nOrder: "
              + orderNo
              + "\nHospital/Clinic: "
              + clinic
              + "\n\nRegards,\n"
              + clinic;
        }
        yield extraMsg;
      }
      case "HRM_APPROVAL" -> {
        String extraMsg =
            req.extra() != null && req.extra().get("message") != null
                ? req.extra().get("message")
                : "Your HR request was updated.";
        if ("EMAIL".equals(channel)) {
          yield "Dear "
              + patient
              + ",\n\n"
              + extraMsg
              + "\n\nHospital: "
              + clinic
              + "\n\nRegards,\nHR — "
              + clinic;
        }
        yield extraMsg;
      }
      default -> clinic + ": Status update " + req.eventCode() + " for " + patient + ".";
    };
  }

  private static String nullTo(String v, String d) {
    return v == null || v.isBlank() ? d : v;
  }

  private static Long resolveHospitalId(String clinicId) {
    if (clinicId == null || clinicId.isBlank()) {
      return 10001L;
    }
    try {
      return Long.parseLong(clinicId.trim());
    } catch (NumberFormatException ex) {
      return 10001L;
    }
  }

  public List<NotificationEntity> recent() {
    return repo.findTop50ByOrderByCreatedAtDesc();
  }

  public List<NotificationEntity> byRecipient(String recipient) {
    if (recipient == null || recipient.isBlank()) {
      return List.of();
    }
    String digits = recipient.replaceAll("\\D", "");
    if (digits.isBlank()) {
      return repo.findTop50ByRecipientOrderByCreatedAtDesc(recipient.trim());
    }
    List<NotificationEntity> rows = repo.findTop50ByRecipientOrderByCreatedAtDesc(digits);
    if (!rows.isEmpty()) return rows;
    if (digits.length() > 10) {
      return repo.findTop50ByRecipientOrderByCreatedAtDesc(digits.substring(digits.length() - 10));
    }
    if (digits.length() == 10) {
      return repo.findTop50ByRecipientOrderByCreatedAtDesc("91" + digits);
    }
    return rows;
  }

  public List<NotificationEntity> byVisit(String visitId) {
    return repo.findByVisitIdOrderByCreatedAtDesc(visitId);
  }
}
