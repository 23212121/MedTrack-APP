package com.medtrack.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.booking.domain.MedicalStoreEntity;
import com.medtrack.booking.domain.MedicineOrderAmountHistoryEntity;
import com.medtrack.booking.domain.MedicineOrderChargesEntity;
import com.medtrack.booking.domain.MedicineOrderDocumentEntity;
import com.medtrack.booking.domain.MedicineOrderEntity;
import com.medtrack.booking.domain.MedicineOrderItemEntity;
import com.medtrack.booking.domain.MedicineOrderNotificationEntity;
import com.medtrack.booking.domain.MedicineOrderPharmacyResponseEntity;
import com.medtrack.booking.domain.MedicineOrderStatusHistoryEntity;
import com.medtrack.booking.repo.MedicalStoreRepository;
import com.medtrack.booking.repo.MedicineOrderAmountHistoryRepository;
import com.medtrack.booking.repo.MedicineOrderChargesRepository;
import com.medtrack.booking.repo.MedicineOrderDocumentRepository;
import com.medtrack.booking.repo.MedicineOrderItemRepository;
import com.medtrack.booking.repo.MedicineOrderNotificationRepository;
import com.medtrack.booking.repo.MedicineOrderPharmacyResponseRepository;
import com.medtrack.booking.repo.MedicineOrderRepository;
import com.medtrack.booking.repo.MedicineOrderStatusHistoryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicineOrderAppService {
  private static final Logger log = LoggerFactory.getLogger(MedicineOrderAppService.class);
  public static final Set<String> OPEN_STATUSES = Set.of("ORDERED", "PENDING");
  public static final Set<String> ALLOWED_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/jpg", "image/png");
  public static final Set<String> ALLOWED_EXT = Set.of(".pdf", ".jpg", ".jpeg", ".png");

  private final MedicineOrderRepository orderRepo;
  private final MedicineOrderItemRepository itemRepo;
  private final MedicineOrderChargesRepository chargesRepo;
  private final MedicineOrderDocumentRepository docRepo;
  private final MedicineOrderAmountHistoryRepository amountHistRepo;
  private final MedicineOrderStatusHistoryRepository statusHistRepo;
  private final MedicineOrderPharmacyResponseRepository responseRepo;
  private final MedicineOrderNotificationRepository notifRepo;
  private final MedicalStoreRepository storeRepo;
  private final ObjectMapper mapper;
  private final S3DocumentStorageService s3;
  private final Path uploadRoot;

  public MedicineOrderAppService(
      MedicineOrderRepository orderRepo,
      MedicineOrderItemRepository itemRepo,
      MedicineOrderChargesRepository chargesRepo,
      MedicineOrderDocumentRepository docRepo,
      MedicineOrderAmountHistoryRepository amountHistRepo,
      MedicineOrderStatusHistoryRepository statusHistRepo,
      MedicineOrderPharmacyResponseRepository responseRepo,
      MedicineOrderNotificationRepository notifRepo,
      MedicalStoreRepository storeRepo,
      ObjectMapper mapper,
      S3DocumentStorageService s3,
      @Value("${medtrack.upload-dir:uploads/patient-documents}") String uploadDir) {
    this.orderRepo = orderRepo;
    this.itemRepo = itemRepo;
    this.chargesRepo = chargesRepo;
    this.docRepo = docRepo;
    this.amountHistRepo = amountHistRepo;
    this.statusHistRepo = statusHistRepo;
    this.responseRepo = responseRepo;
    this.notifRepo = notifRepo;
    this.storeRepo = storeRepo;
    this.mapper = mapper;
    this.s3 = s3;
    this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize().getParent()
        .resolve("medicine-orders");
  }

  public record Actor(
      String loginType,
      Long hospitalId,
      String storeId,
      String doctorId,
      String patientPhone,
      String patientId,
      String patientName,
      String username) {
    public boolean isMedical() {
      return "MEDICAL".equalsIgnoreCase(loginType);
    }

    public boolean isHospital() {
      return "HOSPITAL".equalsIgnoreCase(loginType);
    }

    public boolean isDoctor() {
      return "USER".equalsIgnoreCase(loginType);
    }

    public boolean isPatient() {
      return "PATIENT".equalsIgnoreCase(loginType);
    }

    public String label() {
      if (username != null && !username.isBlank()) return username.trim();
      if (isMedical()) return storeId;
      if (isDoctor()) return doctorId;
      if (isPatient()) return patientName != null ? patientName : patientPhone;
      return hospitalId == null ? "system" : String.valueOf(hospitalId);
    }
  }

  @Transactional
  public Map<String, Object> create(Actor actor, Map<String, Object> body, MultipartFile[] files) {
    if (actor.isMedical()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Medical store cannot create orders");
    }
    Long hospitalId = toLong(body.get("hospitalId"));
    if (hospitalId == null) hospitalId = actor.hospitalId();
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId is required");
    }
    if (actor.hospitalId() != null && !actor.hospitalId().equals(hospitalId) && !actor.isPatient()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hospital mismatch");
    }

    String patientName = firstNonBlank(text(body, "patientName"), actor.patientName());
    String patientPhone = digits(firstNonBlank(text(body, "patientPhone"), actor.patientPhone()));
    if (patientName == null || patientName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientName is required");
    }
    if (patientPhone.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientPhone is required");
    }

    String fulfillment = text(body, "fulfillment");
    if (fulfillment == null || fulfillment.isBlank()) fulfillment = "PICKUP";
    fulfillment = fulfillment.trim().toUpperCase(Locale.ROOT);
    if (!fulfillment.equals("PICKUP") && !fulfillment.equals("DELIVERY")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fulfillment must be PICKUP or DELIVERY");
    }
    String address = text(body, "deliveryAddress");
    if (fulfillment.equals("DELIVERY") && (address == null || address.isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryAddress is required for delivery");
    }

    List<MultipartFile> selected = nonEmpty(files);
    if (selected.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at least one prescription file");
    }
    validateFiles(selected);

    String bookedBy = actor.isPatient() ? "PATIENT" : actor.isDoctor() ? "DOCTOR" : "HOSPITAL";
    MedicineOrderEntity order = new MedicineOrderEntity();
    order.setId(UUID.randomUUID().toString());
    order.setOrderNumber(nextOrderNumber());
    order.setHospitalId(hospitalId);
    order.setPatientId(firstNonBlank(text(body, "patientId"), actor.patientId()));
    order.setPatientName(patientName.trim());
    order.setPatientPhone(patientPhone);
    order.setDoctorId(firstNonBlank(text(body, "doctorId"), actor.isDoctor() ? actor.doctorId() : null));
    order.setDoctorName(text(body, "doctorName"));
    if (order.getDoctorName() == null && actor.isDoctor()) order.setDoctorName(actor.username());
    order.setFulfillment(fulfillment);
    order.setDeliveryAddress(address);
    order.setNotes(text(body, "notes"));
    order.setStatus("PENDING");
    order.setStatusCode(8);
    order.setAmountStatus("NOT_CALCULATED");
    order.setBookedBy(bookedBy);
    order.setCreatedAt(Instant.now());
    order.setCreatedBy(actor.label());
    orderRepo.save(order);
    addStatus(order.getId(), null, "ORDERED", actor.label(), "Order created");
    addStatus(order.getId(), "ORDERED", "PENDING", "system", "Waiting for medical store");
    storeDocuments(order, selected, actor.label(), true);

    notifyHospitalStores(
        hospitalId,
        order.getId(),
        "New medicine order",
        "A new medicine order " + order.getOrderNumber() + " has been received. Please review the prescription.");

    return detail(order, actor, false);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(
      Actor actor,
      String status,
      String amountStatus,
      String q,
      String fulfillment,
      String from,
      String to) {
    List<MedicineOrderEntity> rows = loadForActor(actor);
    LocalDate fromDate = parseDate(from);
    LocalDate toDate = parseDate(to);
    String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    List<Map<String, Object>> out = new ArrayList<>();
    for (MedicineOrderEntity o : rows) {
      if (!visibleTo(actor, o)) continue;
      if (status != null && !status.isBlank() && !status.equalsIgnoreCase(o.getStatus())) continue;
      if (amountStatus != null
          && !amountStatus.isBlank()
          && !amountStatus.equalsIgnoreCase(blankTo(o.getAmountStatus(), ""))) {
        continue;
      }
      if (fulfillment != null
          && !fulfillment.isBlank()
          && !fulfillment.equalsIgnoreCase(o.getFulfillment())) {
        continue;
      }
      if (fromDate != null || toDate != null) {
        LocalDate created =
            o.getCreatedAt() == null
                ? null
                : LocalDate.ofInstant(o.getCreatedAt(), ZoneId.systemDefault());
        if (created == null) continue;
        if (fromDate != null && created.isBefore(fromDate)) continue;
        if (toDate != null && created.isAfter(toDate)) continue;
      }
      if (!query.isBlank()) {
        String hay =
            (blankTo(o.getOrderNumber(), "")
                    + " "
                    + blankTo(o.getPatientName(), "")
                    + " "
                    + blankTo(o.getPatientPhone(), "")
                    + " "
                    + blankTo(o.getAssignedStoreName(), ""))
                .toLowerCase(Locale.ROOT);
        if (!hay.contains(query)) continue;
      }
      out.add(summary(o, actor));
    }
    return Map.of("count", out.size(), "orders", out);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(Actor actor, String id) {
    return detail(require(id), actor, true);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> counts(Actor actor) {
    Map<String, Object> c = new LinkedHashMap<>();
    List<MedicineOrderEntity> rows = loadForActor(actor);
    int neu = 0, pending = 0, inProcess = 0, waiting = 0, accepted = 0, ready = 0, completed = 0, canceled = 0;
    for (MedicineOrderEntity o : rows) {
      if (!visibleTo(actor, o)) continue;
      switch (o.getStatus()) {
        case "ORDERED", "PENDING" -> {
          pending++;
          if (o.getAssignedStoreId() == null) neu++;
        }
        case "IN_PROCESS" -> inProcess++;
        case "WAITING_FOR_PATIENT_APPROVAL" -> waiting++;
        case "AMOUNT_ACCEPTED" -> accepted++;
        case "MEDICINE_READY" -> ready++;
        case "COMPLETED" -> completed++;
        case "CANCELED" -> canceled++;
        default -> {}
      }
    }
    c.put("newOrders", neu);
    c.put("pending", pending);
    c.put("inProcess", inProcess);
    c.put("waitingApproval", waiting);
    c.put("amountAccepted", accepted);
    c.put("medicineReady", ready);
    c.put("completed", completed);
    c.put("canceled", canceled);
    c.put("unreadNotifications", unreadCount(actor));
    return c;
  }

  @Transactional
  public Map<String, Object> accept(Actor actor, String id) {
    requireMedical(actor);
    MedicalStoreEntity store = requireActiveStore(actor);
    MedicineOrderEntity current = require(id);
    assertSameHospital(actor, current);
    if (store.getId().equals(current.getAssignedStoreId()) && "PENDING".equals(current.getStatus())) {
      current.setStatus("IN_PROCESS");
      current.setStatusCode(1);
      current.setPendingReason(null);
      current.setUpdatedAt(Instant.now());
      current.setUpdatedBy(actor.label());
      orderRepo.save(current);
      addStatus(id, "PENDING", "IN_PROCESS", actor.label(), "Resumed by " + store.getStoreName());
      notifyPatient(
          current,
          "Order in process",
          "Your medicine order " + current.getOrderNumber() + " is being processed again.");
      return detail(current, actor, true);
    }
    if (!OPEN_STATUSES.contains(current.getStatus()) || current.getAssignedStoreId() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This order has already been accepted by another medical store.");
    }
    int locked = orderRepo.lockAccept(id, store.getId(), store.getStoreName(), actor.label());
    if (locked == 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This order has already been accepted by another medical store.");
    }
    MedicineOrderEntity order = require(id);
    order.setStatus("IN_PROCESS");
    order.setStatusCode(1);
    order.setPendingReason(null);
    orderRepo.save(order);
    addStatus(id, current.getStatus(), "IN_PROCESS", actor.label(), "Accepted by " + store.getStoreName());
    saveResponse(id, store.getId(), "ACCEPT", null);
    notifyPatient(
        order,
        "Order accepted",
        "Your medicine order " + order.getOrderNumber() + " has been accepted and is currently being processed.");
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> reject(Actor actor, String id, Map<String, Object> body) {
    requireMedical(actor);
    MedicalStoreEntity store = requireActiveStore(actor);
    MedicineOrderEntity order = require(id);
    assertSameHospital(actor, order);
    if (order.getAssignedStoreId() != null && !store.getId().equals(order.getAssignedStoreId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This order is assigned to another store.");
    }
    if (store.getId().equals(order.getAssignedStoreId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Use release to return an accepted order to the pool");
    }
    String reason = firstNonBlank(text(body, "reason"), "Rejected");
    saveResponse(id, store.getId(), "REJECT", reason);
    return Map.of("message", "Order remains available for other medical stores", "order", summary(order, actor));
  }

  @Transactional
  public Map<String, Object> markPending(Actor actor, String id, Map<String, Object> body) {
    requireMedical(actor);
    MedicineOrderEntity order = requireAssigned(actor, id);
    if (!Set.of("IN_PROCESS", "WAITING_FOR_PATIENT_APPROVAL").contains(order.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only in-process orders can be marked pending");
    }
    String reason = text(body, "reason");
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pending reason is required");
    }
    String prev = order.getStatus();
    order.setStatus("PENDING");
    order.setStatusCode(8);
    order.setPendingReason(reason.trim());
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    addStatus(id, prev, "PENDING", actor.label(), reason.trim());
    notifyPatient(
        order,
        "Order pending",
        "Your medicine order " + order.getOrderNumber() + " is pending. Reason: " + reason.trim());
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> saveQuote(Actor actor, String id, Map<String, Object> body, boolean send) {
    requireMedical(actor);
    MedicineOrderEntity order = requireAssigned(actor, id);
    String status = order.getStatus();
    boolean revisingApproved = "AMOUNT_ACCEPTED".equals(status) && send;
    if (!Set.of("IN_PROCESS", "PENDING", "WAITING_FOR_PATIENT_APPROVAL", "AMOUNT_ACCEPTED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit quote in status " + status);
    }
    if ("AMOUNT_ACCEPTED".equals(status) && !send) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Approved amount is locked. Send a revised quotation instead.");
    }
    if ("AMOUNT_ACCEPTED".equals(status) && send) {
      String reason = text(body, "reason");
      if (reason == null || reason.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision reason is required");
      }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
    if (items == null || items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one medicine");
    }
    itemRepo.deleteByOrderId(id);
    double subtotal = 0;
    int i = 0;
    List<Map<String, Object>> snapItems = new ArrayList<>();
    for (Map<String, Object> row : items) {
      String prescribed = firstNonBlank(text(row, "prescribedName"), text(row, "medicineName"));
      String medicine = firstNonBlank(text(row, "medicineName"), prescribed);
      if (medicine == null || medicine.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicineName is required");
      }
      double qty = toDouble(row.get("quantity"), 0);
      double unit = toDouble(row.get("unitPrice"), 0);
      if (qty <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than 0");
      }
      if (unit < 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPrice cannot be negative");
      }
      double line = round2(qty * unit);
      subtotal += line;
      MedicineOrderItemEntity item = new MedicineOrderItemEntity();
      item.setId(UUID.randomUUID().toString());
      item.setOrderId(id);
      item.setPrescribedName(prescribed.trim());
      item.setMedicineName(medicine.trim());
      item.setQuantity(qty);
      item.setUnitPrice(unit);
      item.setLineTotal(line);
      item.setAvailability(firstNonBlank(text(row, "availability"), "AVAILABLE"));
      item.setSubstituteName(text(row, "substituteName"));
      item.setSubstituteReason(text(row, "substituteReason"));
      item.setSortOrder(i++);
      itemRepo.save(item);
      snapItems.add(itemMap(item, true));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> chargesIn =
        body.get("charges") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    double delivery = Math.max(0, toDouble(chargesIn.get("deliveryCharge"), 0));
    double packaging = Math.max(0, toDouble(chargesIn.get("packagingCharge"), 0));
    double tax = Math.max(0, toDouble(chargesIn.get("tax"), 0));
    double other = Math.max(0, toDouble(chargesIn.get("otherCharges"), 0));
    double discount = Math.max(0, toDouble(chargesIn.get("discount"), 0));
    double grand = round2(subtotal + delivery + packaging + tax + other - discount);
    if (grand < 0) grand = 0;

    MedicineOrderChargesEntity charges =
        chargesRepo.findById(id).orElseGet(MedicineOrderChargesEntity::new);
    charges.setOrderId(id);
    charges.setDeliveryCharge(delivery);
    charges.setPackagingCharge(packaging);
    charges.setTax(tax);
    charges.setOtherCharges(other);
    charges.setDiscount(discount);
    charges.setMedicineSubtotal(round2(subtotal));
    charges.setGrandTotal(grand);
    int version = Math.max(1, charges.getQuoteVersion());
    if (revisingApproved || ("WAITING_FOR_PATIENT_APPROVAL".equals(status) && send)) {
      version = version + 1;
    }
    charges.setQuoteVersion(version);
    charges.setDraft(!send);
    chargesRepo.save(charges);

    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    if (send) {
      order.setCurrentAmount(grand);
      order.setAmountStatus("SENT");
      String prev = order.getStatus();
      order.setStatus("WAITING_FOR_PATIENT_APPROVAL");
      order.setStatusCode(2);
      orderRepo.save(order);
      addStatus(
          id,
          prev,
          "WAITING_FOR_PATIENT_APPROVAL",
          actor.label(),
          revisingApproved ? "Revised amount sent" : "Amount sent to patient");
      saveAmountHistory(id, version, grand, snapItems, charges, send ? "SENT" : "DRAFT", text(body, "reason"), actor.label());
      notifyPatient(
          order,
          revisingApproved ? "Revised medicine amount" : "Medicine amount ready",
          "Your medicine order "
              + order.getOrderNumber()
              + " amount is ₹"
              + formatAmt(grand)
              + " from "
              + blankTo(order.getAssignedStoreName(), "the medical store")
              + ". Please review and confirm.");
    } else {
      order.setCurrentAmount(grand);
      order.setAmountStatus("DRAFT");
      orderRepo.save(order);
    }
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> acceptAmount(Actor actor, String id) {
    MedicineOrderEntity order = require(id);
    assertPatientOrHospitalRead(actor, order, true);
    if (!"WAITING_FOR_PATIENT_APPROVAL".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is not waiting for approval");
    }
    String prev = order.getStatus();
    order.setStatus("AMOUNT_ACCEPTED");
    order.setStatusCode(3);
    order.setAmountStatus("ACCEPTED");
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    addStatus(id, prev, "AMOUNT_ACCEPTED", actor.label(), "Patient accepted amount");
    markLatestHistory(id, "ACCEPTED");
    notifyStore(
        order,
        "Amount accepted",
        "The patient has accepted the medicine amount for " + order.getOrderNumber() + ". You can prepare the order.");
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> rejectAmount(Actor actor, String id, Map<String, Object> body) {
    MedicineOrderEntity order = require(id);
    assertPatientOrHospitalRead(actor, order, true);
    if (!"WAITING_FOR_PATIENT_APPROVAL".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is not waiting for approval");
    }
    String reason = firstNonBlank(text(body, "reason"), "Rejected");
    String comments = text(body, "comments");
    String note = comments == null || comments.isBlank() ? reason : reason + " — " + comments;
    String prev = order.getStatus();
    order.setStatus("IN_PROCESS");
    order.setStatusCode(1);
    order.setAmountStatus("REJECTED");
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    addStatus(id, prev, "IN_PROCESS", actor.label(), note);
    markLatestHistory(id, "REJECTED");
    notifyStore(
        order,
        "Amount rejected",
        "The patient rejected the amount for " + order.getOrderNumber() + ". Reason: " + note);
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> markReady(Actor actor, String id) {
    requireMedical(actor);
    MedicineOrderEntity order = requireAssigned(actor, id);
    if (!"AMOUNT_ACCEPTED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be accepted before marking ready");
    }
    String prev = order.getStatus();
    order.setStatus("MEDICINE_READY");
    order.setStatusCode(5);
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    addStatus(id, prev, "MEDICINE_READY", actor.label(), "Medicines prepared");
    notifyPatient(order, "Medicine ready", "Your medicine order " + order.getOrderNumber() + " is ready.");
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> complete(Actor actor, String id) {
    requireMedical(actor);
    MedicineOrderEntity order = requireAssigned(actor, id);
    if (!"MEDICINE_READY".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be MEDICINE_READY to complete");
    }
    String prev = order.getStatus();
    order.setStatus("COMPLETED");
    order.setStatusCode(10);
    order.setCompletedAt(Instant.now());
    order.setCompletedBy(actor.label());
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    addStatus(id, prev, "COMPLETED", actor.label(), "Order completed");
    notifyPatient(order, "Order completed", "Your medicine order " + order.getOrderNumber() + " is completed.");
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> cancel(Actor actor, String id, Map<String, Object> body) {
    MedicineOrderEntity order = require(id);
    assertCanView(actor, order);
    String status = order.getStatus();
    if (Set.of("MEDICINE_READY", "COMPLETED", "CANCELED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order cannot be canceled in status " + status);
    }
    if (actor.isMedical()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medical store should release the order instead");
    }
    if (actor.isPatient()) {
      assertPatientOwns(actor, order);
    }
    String reason = firstNonBlank(text(body, "reason"), "Canceled");
    String prev = order.getStatus();
    order.setStatus("CANCELED");
    order.setStatusCode(9);
    order.setCancelReason(reason);
    order.setCancelledBy(actor.label());
    order.setCancelledAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    addStatus(id, prev, "CANCELED", actor.label(), reason);
    notifyHospitalStores(
        order.getHospitalId(),
        id,
        "Order canceled",
        "Medicine order " + order.getOrderNumber() + " was canceled. Reason: " + reason);
    notifyPatient(order, "Order canceled", "Medicine order " + order.getOrderNumber() + " was canceled.");
    if (order.getAssignedStoreId() != null) {
      notifyStore(order, "Order canceled", "Medicine order " + order.getOrderNumber() + " was canceled.");
    }
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> release(Actor actor, String id, Map<String, Object> body) {
    requireMedical(actor);
    MedicineOrderEntity order = requireAssigned(actor, id);
    if (Set.of("MEDICINE_READY", "COMPLETED", "CANCELED").contains(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot release order in status " + order.getStatus());
    }
    String reason = firstNonBlank(text(body, "reason"), "Released by store");
    String prev = order.getStatus();
    String storeId = order.getAssignedStoreId();
    order.setAssignedStoreId(null);
    order.setAssignedStoreName(null);
    order.setStatus("PENDING");
    order.setStatusCode(8);
    order.setAmountStatus("NOT_CALCULATED");
    order.setCurrentAmount(null);
    order.setUpdatedAt(Instant.now());
    order.setUpdatedBy(actor.label());
    orderRepo.save(order);
    itemRepo.deleteByOrderId(id);
    chargesRepo.deleteById(id);
    addStatus(id, prev, "PENDING", actor.label(), reason);
    saveResponse(id, storeId, "RELEASE", reason);
    notifyHospitalStores(
        order.getHospitalId(),
        id,
        "Order available again",
        "Medicine order " + order.getOrderNumber() + " is available again.");
    notifyPatient(
        order,
        "Store released order",
        "The medical store released order " + order.getOrderNumber() + ". It is waiting for another store.");
    return detail(order, actor, true);
  }

  @Transactional
  public Map<String, Object> markUnclear(Actor actor, String id, Map<String, Object> body) {
    requireMedical(actor);
    MedicineOrderEntity order = requireAssigned(actor, id);
    String note = firstNonBlank(text(body, "note"), "Prescription is unclear");
    notifyPatient(
        order,
        "Prescription unclear",
        "The uploaded prescription for " + order.getOrderNumber() + " is unclear. Please upload a clear prescription. " + note);
    addStatus(id, order.getStatus(), order.getStatus(), actor.label(), note);
    return Map.of("message", "Patient notified to upload a clearer prescription", "order", summary(order, actor));
  }

  @Transactional
  public Map<String, Object> uploadDocuments(Actor actor, String id, MultipartFile[] files) {
    MedicineOrderEntity order = require(id);
    if (actor.isPatient()) {
      assertPatientOwns(actor, order);
    } else if (actor.isMedical()) {
      requireAssigned(actor, id);
    } else if (actor.isHospital() || actor.isDoctor()) {
      assertSameHospital(actor, order);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }
    if (Set.of("COMPLETED", "CANCELED").contains(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload files to a closed order");
    }
    List<MultipartFile> selected = nonEmpty(files);
    if (selected.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at least one file");
    }
    validateFiles(selected);
    storeDocuments(order, selected, actor.label(), true);
    if (order.getAssignedStoreId() != null) {
      notifyStore(
          order,
          "New prescription uploaded",
          "A new prescription was uploaded for " + order.getOrderNumber() + ".");
    }
    return detail(order, actor, true);
  }

  @Transactional(readOnly = true)
  public FilePayload documentFile(Actor actor, String orderId, String docId) {
    MedicineOrderEntity order = require(orderId);
    assertCanView(actor, order);
    if (actor.isMedical()
        && order.getAssignedStoreId() != null
        && !order.getAssignedStoreId().equals(actor.storeId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Prescription is assigned to another store");
    }
    MedicineOrderDocumentEntity doc =
        docRepo
            .findById(docId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    if (!orderId.equals(doc.getOrderId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
    }
    String stored = doc.getFilePath();
    if (stored != null && !stored.isBlank() && s3.isEnabled()) {
      byte[] fromS3 = s3.download(stored).orElse(null);
      if (fromS3 != null) {
        return new FilePayload(doc.getFileName(), doc.getContentType(), fromS3);
      }
    }
    Path path = Path.of(stored == null ? "" : stored).normalize();
    if (!Files.isRegularFile(path)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File missing on disk and Amazon S3");
    }
    try {
      return new FilePayload(doc.getFileName(), doc.getContentType(), Files.readAllBytes(path));
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
    }
  }

  public record FilePayload(String fileName, String contentType, byte[] bytes) {}

  @Transactional(readOnly = true)
  public Map<String, Object> notifications(Actor actor) {
    List<MedicineOrderNotificationEntity> rows;
    if (actor.isMedical()) {
      rows = notifRepo.findByStoreIdOrderByCreatedAtDesc(actor.storeId());
    } else if (actor.isPatient()) {
      rows = notifRepo.findByPatientPhoneOrderByCreatedAtDesc(digits(actor.patientPhone()));
    } else if (actor.hospitalId() != null) {
      rows = notifRepo.findByHospitalIdAndAudienceOrderByCreatedAtDesc(actor.hospitalId(), "HOSPITAL");
    } else {
      rows = List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (MedicineOrderNotificationEntity n : rows) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", n.getId());
      m.put("orderId", n.getOrderId());
      m.put("title", n.getTitle());
      m.put("message", n.getMessage());
      m.put("read", n.isReadFlag());
      m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
      out.add(m);
    }
    return Map.of("count", out.size(), "notifications", out);
  }

  @Transactional
  public Map<String, Object> markRead(Actor actor, String notificationId) {
    MedicineOrderNotificationEntity n =
        notifRepo
            .findById(notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    n.setReadFlag(true);
    notifRepo.save(n);
    return Map.of("ok", true);
  }

  private List<MedicineOrderEntity> loadForActor(Actor actor) {
    if (actor.isPatient()) {
      return orderRepo.findByPatientPhoneOrderByCreatedAtDesc(digits(actor.patientPhone()));
    }
    if (actor.isMedical()) {
      if (actor.hospitalId() == null) return List.of();
      return orderRepo.findByHospitalIdOrderByCreatedAtDesc(actor.hospitalId());
    }
    if (actor.hospitalId() != null) {
      return orderRepo.findByHospitalIdOrderByCreatedAtDesc(actor.hospitalId());
    }
    if (actor.isDoctor() && actor.doctorId() != null) {
      return orderRepo.findByDoctorIdOrderByCreatedAtDesc(actor.doctorId());
    }
    return List.of();
  }

  private boolean visibleTo(Actor actor, MedicineOrderEntity o) {
    if (actor.isPatient()) {
      return digits(actor.patientPhone()).equals(digits(o.getPatientPhone()));
    }
    if (actor.isMedical()) {
      if (actor.hospitalId() != null && !actor.hospitalId().equals(o.getHospitalId())) return false;
      if (o.getAssignedStoreId() == null && OPEN_STATUSES.contains(o.getStatus())) {
        return !responseRepo.existsByOrderIdAndStoreIdAndAction(o.getId(), actor.storeId(), "REJECT");
      }
      return actor.storeId() != null && actor.storeId().equals(o.getAssignedStoreId());
    }
    if (actor.hospitalId() != null) {
      return actor.hospitalId().equals(o.getHospitalId());
    }
    return actor.isDoctor() && actor.doctorId() != null && actor.doctorId().equals(o.getDoctorId());
  }

  private void assertCanView(Actor actor, MedicineOrderEntity o) {
    if (!visibleTo(actor, o) && !(actor.isMedical() && actor.hospitalId() != null && actor.hospitalId().equals(o.getHospitalId()) && OPEN_STATUSES.contains(o.getStatus()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this order");
    }
  }

  private void assertPatientOwns(Actor actor, MedicineOrderEntity o) {
    if (!digits(actor.patientPhone()).equals(digits(o.getPatientPhone()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order is not for this patient");
    }
  }

  private void assertPatientOrHospitalRead(Actor actor, MedicineOrderEntity o, boolean patientAction) {
    if (actor.isPatient()) {
      assertPatientOwns(actor, o);
      return;
    }
    if (patientAction) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the patient can approve or reject the amount");
    }
    assertCanView(actor, o);
  }

  private void assertSameHospital(Actor actor, MedicineOrderEntity o) {
    if (actor.hospitalId() != null && !actor.hospitalId().equals(o.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order belongs to another hospital");
    }
  }

  private MedicineOrderEntity requireAssigned(Actor actor, String id) {
    MedicineOrderEntity order = require(id);
    assertSameHospital(actor, order);
    if (actor.storeId() == null || !actor.storeId().equals(order.getAssignedStoreId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order is not assigned to this store");
    }
    return order;
  }

  private void requireMedical(Actor actor) {
    if (!actor.isMedical()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the medical store can process this order");
    }
  }

  private MedicalStoreEntity requireActiveStore(Actor actor) {
    if (actor.storeId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "medicalStoreId is required");
    }
    MedicalStoreEntity store = storeRepo.findById(actor.storeId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical store not found"));
    if (!"ACTIVE".equalsIgnoreCase(store.getStatus())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Medical store is inactive");
    }
    return store;
  }

  private MedicineOrderEntity require(String id) {
    return orderRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine order not found"));
  }

  private Map<String, Object> summary(MedicineOrderEntity o, Actor actor) {
    Map<String, Object> m = baseOrder(o, actor);
    List<MedicineOrderDocumentEntity> docs = docRepo.findByOrderIdOrderByCreatedAtDesc(o.getId());
    m.put("prescriptionCount", docs.size());
    m.put("documents", docs.stream().map(this::docMap).toList());
    boolean showAmount = canSeeAmount(actor, o);
    m.put("amount", showAmount ? o.getCurrentAmount() : null);
    m.put("amountVisible", showAmount);
    return m;
  }

  private Map<String, Object> detail(MedicineOrderEntity o, Actor actor, boolean full) {
    assertCanView(actor, o);
    Map<String, Object> m = summary(o, actor);
    if (!full) return m;
    boolean showQuote = canSeeAmount(actor, o) || actor.isMedical();
    List<MedicineOrderItemEntity> items = itemRepo.findByOrderIdOrderBySortOrderAsc(o.getId());
    m.put("items", showQuote ? items.stream().map(it -> itemMap(it, true)).toList() : List.of());
    chargesRepo
        .findById(o.getId())
        .ifPresentOrElse(
            c -> {
              if (showQuote && (actor.isMedical() || !c.isDraft())) {
                m.put("charges", chargesMap(c));
              } else {
                m.put("charges", null);
              }
            },
            () -> m.put("charges", null));
    if (actor.isHospital() || actor.isMedical() || actor.isPatient()) {
      m.put(
          "amountHistory",
          amountHistRepo.findByOrderIdOrderByVersionDesc(o.getId()).stream().map(this::histMap).toList());
      m.put(
          "statusHistory",
          statusHistRepo.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream().map(this::statusMap).toList());
    }
    return m;
  }

  private boolean canSeeAmount(Actor actor, MedicineOrderEntity o) {
    if (actor.isMedical()) {
      return true;
    }
    if (actor.isHospital() || actor.isDoctor()) {
      return o.getCurrentAmount() != null && !"DRAFT".equalsIgnoreCase(blankTo(o.getAmountStatus(), ""));
    }
    if (actor.isPatient()) {
      String st = blankTo(o.getAmountStatus(), "NOT_CALCULATED");
      return Set.of("SENT", "ACCEPTED", "REJECTED").contains(st);
    }
    return false;
  }

  private Map<String, Object> baseOrder(MedicineOrderEntity o, Actor actor) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", o.getId());
    m.put("orderNumber", o.getOrderNumber());
    m.put("hospitalId", o.getHospitalId());
    m.put("patientName", o.getPatientName());
    m.put("patientPhone", actor.isMedical() && o.getAssignedStoreId() == null
        ? maskPhone(o.getPatientPhone())
        : o.getPatientPhone());
    m.put("patientId", o.getPatientId());
    m.put("doctorId", o.getDoctorId());
    m.put("doctorName", o.getDoctorName());
    m.put("fulfillment", o.getFulfillment());
    m.put("deliveryAddress", o.getDeliveryAddress());
    m.put("notes", o.getNotes());
    m.put("pendingReason", o.getPendingReason());
    m.put("status", o.getStatus());
    m.put("statusCode", o.getStatusCode());
    m.put("assignedStoreId", o.getAssignedStoreId());
    m.put("assignedStoreName", o.getAssignedStoreName());
    m.put("amountStatus", o.getAmountStatus());
    m.put("bookedBy", o.getBookedBy());
    m.put("cancelReason", o.getCancelReason());
    m.put("cancelledAt", o.getCancelledAt() != null ? o.getCancelledAt().toString() : null);
    m.put("completedAt", o.getCompletedAt() != null ? o.getCompletedAt().toString() : null);
    m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
    return m;
  }

  private Map<String, Object> itemMap(MedicineOrderItemEntity it, boolean includePrice) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", it.getId());
    m.put("prescribedName", it.getPrescribedName());
    m.put("medicineName", it.getMedicineName());
    m.put("quantity", it.getQuantity());
    m.put("availability", it.getAvailability());
    m.put("substituteName", it.getSubstituteName());
    m.put("substituteReason", it.getSubstituteReason());
    if (includePrice) {
      m.put("unitPrice", it.getUnitPrice());
      m.put("lineTotal", it.getLineTotal());
    }
    return m;
  }

  private Map<String, Object> chargesMap(MedicineOrderChargesEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("deliveryCharge", c.getDeliveryCharge());
    m.put("packagingCharge", c.getPackagingCharge());
    m.put("tax", c.getTax());
    m.put("otherCharges", c.getOtherCharges());
    m.put("discount", c.getDiscount());
    m.put("medicineSubtotal", c.getMedicineSubtotal());
    m.put("grandTotal", c.getGrandTotal());
    m.put("draft", c.isDraft());
    m.put("quoteVersion", c.getQuoteVersion());
    return m;
  }

  private Map<String, Object> docMap(MedicineOrderDocumentEntity d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId());
    m.put("fileName", d.getFileName());
    m.put("contentType", d.getContentType());
    m.put("latest", d.isLatest());
    m.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
    m.put("previewUrl", "/api/medicine-orders/" + d.getOrderId() + "/documents/" + d.getId());
    return m;
  }

  private Map<String, Object> histMap(MedicineOrderAmountHistoryEntity h) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", h.getId());
    m.put("version", h.getVersion());
    m.put("grandTotal", h.getGrandTotal());
    m.put("status", h.getStatus());
    m.put("reason", h.getReason());
    m.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
    m.put("createdBy", h.getCreatedBy());
    return m;
  }

  private Map<String, Object> statusMap(MedicineOrderStatusHistoryEntity h) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", h.getId());
    m.put("previousStatus", h.getPreviousStatus());
    m.put("newStatus", h.getNewStatus());
    m.put("actor", h.getActor());
    m.put("note", h.getNote());
    m.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
    return m;
  }

  private void storeDocuments(
      MedicineOrderEntity order, List<MultipartFile> files, String actor, boolean markLatest) {
    if (markLatest) {
      docRepo.clearLatest(order.getId());
    }
    Path destDir = uploadRoot.resolve(order.getId());
    try {
      Files.createDirectories(destDir);
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create upload folder");
    }
    boolean first = true;
    int i = 0;
    for (MultipartFile file : files) {
      i++;
      String original = safeName(file.getOriginalFilename(), i);
      String docId = UUID.randomUUID().toString();
      String storedName = docId + "_" + original;
      Path target = destDir.resolve(storedName);
      byte[] bytes;
      try {
        bytes = file.getBytes();
        Files.write(target, bytes);
      } catch (IOException ex) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save " + original);
      }
      String storedPath = target.toString();
      if (s3.isEnabled()) {
        String s3Key = s3.medicineOrderObjectKey(order.getId(), storedName);
        try {
          storedPath =
              s3.upload(
                      s3Key,
                      new java.io.ByteArrayInputStream(bytes),
                      bytes.length,
                      file.getContentType())
                  .orElse(target.toString());
        } catch (RuntimeException ex) {
          log.warn("Amazon S3 upload skipped for medicine order {}; saved locally: {}", s3Key, ex.getMessage());
          storedPath = target.toString();
        }
      }
      MedicineOrderDocumentEntity doc = new MedicineOrderDocumentEntity();
      doc.setId(docId);
      doc.setOrderId(order.getId());
      doc.setFileName(original);
      doc.setContentType(file.getContentType());
      doc.setFilePath(storedPath);
      doc.setLatest(first);
      doc.setCreatedAt(Instant.now());
      doc.setCreatedBy(actor);
      docRepo.save(doc);
      first = false;
    }
  }

  private void validateFiles(List<MultipartFile> files) {
    if (files.size() > 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 8 prescription files");
    }
    for (MultipartFile f : files) {
      if (f.getSize() > 8L * 1024 * 1024) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each file must be 8 MB or smaller");
      }
      String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase(Locale.ROOT);
      String type = f.getContentType() == null ? "" : f.getContentType().toLowerCase(Locale.ROOT);
      boolean extOk = ALLOWED_EXT.stream().anyMatch(name::endsWith);
      boolean typeOk = type.isBlank() || ALLOWED_TYPES.contains(type) || type.equals("image/jpg");
      if (!extOk && !typeOk) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPG, JPEG, and PNG files are allowed");
      }
    }
  }

  private synchronized String nextOrderNumber() {
    int year = Year.now().getValue();
    String prefix = "MED-" + year + "-";
    String max = orderRepo.findMaxOrderNumber(prefix + "%");
    int next = 1;
    if (max != null && max.length() > prefix.length()) {
      try {
        next = Integer.parseInt(max.substring(prefix.length())) + 1;
      } catch (NumberFormatException ignored) {
        next = 1;
      }
    }
    return prefix + String.format("%06d", next);
  }

  private void addStatus(String orderId, String previous, String next, String actor, String note) {
    MedicineOrderStatusHistoryEntity h = new MedicineOrderStatusHistoryEntity();
    h.setId(UUID.randomUUID().toString());
    h.setOrderId(orderId);
    h.setPreviousStatus(previous);
    h.setNewStatus(next);
    h.setActor(actor);
    h.setNote(note);
    h.setCreatedAt(Instant.now());
    statusHistRepo.save(h);
  }

  private void saveResponse(String orderId, String storeId, String action, String reason) {
    MedicineOrderPharmacyResponseEntity r = new MedicineOrderPharmacyResponseEntity();
    r.setId(UUID.randomUUID().toString());
    r.setOrderId(orderId);
    r.setStoreId(storeId);
    r.setAction(action);
    r.setReason(reason);
    r.setCreatedAt(Instant.now());
    responseRepo.save(r);
  }

  private void saveAmountHistory(
      String orderId,
      int version,
      double grand,
      List<Map<String, Object>> items,
      MedicineOrderChargesEntity charges,
      String status,
      String reason,
      String actor) {
    MedicineOrderAmountHistoryEntity h = new MedicineOrderAmountHistoryEntity();
    h.setId(UUID.randomUUID().toString());
    h.setOrderId(orderId);
    h.setVersion(version);
    h.setGrandTotal(grand);
    h.setStatus(status);
    h.setReason(reason);
    h.setCreatedBy(actor);
    h.setCreatedAt(Instant.now());
    try {
      h.setSnapshotJson(mapper.writeValueAsString(Map.of("items", items, "charges", chargesMap(charges))));
    } catch (JsonProcessingException ex) {
      h.setSnapshotJson("{}");
    }
    amountHistRepo.save(h);
  }

  private void markLatestHistory(String orderId, String status) {
    List<MedicineOrderAmountHistoryEntity> rows = amountHistRepo.findByOrderIdOrderByVersionDesc(orderId);
    if (!rows.isEmpty()) {
      MedicineOrderAmountHistoryEntity latest = rows.get(0);
      latest.setStatus(status);
      amountHistRepo.save(latest);
    }
  }

  private void notifyHospitalStores(Long hospitalId, String orderId, String title, String message) {
    List<MedicalStoreEntity> stores =
        storeRepo.findByHospitalIdAndStatusOrderByStoreNameAsc(hospitalId, "ACTIVE");
    for (MedicalStoreEntity store : stores) {
      saveNotif(hospitalId, store.getId(), null, "STORE", orderId, title, message);
    }
    saveNotif(hospitalId, null, null, "HOSPITAL", orderId, title, message);
  }

  private void notifyPatient(MedicineOrderEntity order, String title, String message) {
    saveNotif(order.getHospitalId(), null, order.getPatientPhone(), "PATIENT", order.getId(), title, message);
  }

  private void notifyStore(MedicineOrderEntity order, String title, String message) {
    if (order.getAssignedStoreId() == null) return;
    saveNotif(
        order.getHospitalId(),
        order.getAssignedStoreId(),
        null,
        "STORE",
        order.getId(),
        title,
        message);
  }

  private void saveNotif(
      Long hospitalId,
      String storeId,
      String phone,
      String audience,
      String orderId,
      String title,
      String message) {
    MedicineOrderNotificationEntity n = new MedicineOrderNotificationEntity();
    n.setId(UUID.randomUUID().toString());
    n.setHospitalId(hospitalId);
    n.setStoreId(storeId);
    n.setPatientPhone(phone);
    n.setAudience(audience);
    n.setOrderId(orderId);
    n.setTitle(title);
    n.setMessage(message);
    n.setReadFlag(false);
    n.setCreatedAt(Instant.now());
    notifRepo.save(n);
  }

  private long unreadCount(Actor actor) {
    if (actor.isMedical() && actor.storeId() != null) {
      return notifRepo.countByStoreIdAndReadFlagFalse(actor.storeId());
    }
    if (actor.isPatient() && actor.patientPhone() != null) {
      return notifRepo.countByPatientPhoneAndReadFlagFalse(digits(actor.patientPhone()));
    }
    return 0;
  }

  private static List<MultipartFile> nonEmpty(MultipartFile[] files) {
    List<MultipartFile> out = new ArrayList<>();
    if (files == null) return out;
    for (MultipartFile f : files) {
      if (f != null && !f.isEmpty()) out.add(f);
    }
    return out;
  }

  private static String safeName(String name, int index) {
    String base = name == null || name.isBlank() ? "prescription-" + index : name;
    String cleaned = base.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_").trim();
    if (cleaned.length() > 180) cleaned = cleaned.substring(0, 180);
    return cleaned;
  }

  private static String text(Map<String, Object> body, String key) {
    if (body == null) return null;
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static String firstNonBlank(String... vals) {
    if (vals == null) return null;
    for (String v : vals) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private static String digits(String raw) {
    return raw == null ? "" : raw.replaceAll("\\D", "");
  }

  private static String blankTo(String v, String fallback) {
    return v == null || v.isBlank() ? fallback : v;
  }

  private static String maskPhone(String phone) {
    String d = digits(phone);
    if (d.length() < 4) return "****";
    return "******" + d.substring(d.length() - 4);
  }

  private static Long toLong(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.longValue();
    try {
      String s = String.valueOf(v).trim();
      if (s.isEmpty()) return null;
      return Long.parseLong(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static double toDouble(Object v, double fallback) {
    if (v == null) return fallback;
    if (v instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static String formatAmt(double v) {
    return String.format(Locale.US, "%.2f", v);
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return LocalDate.parse(raw.trim());
    } catch (Exception ex) {
      return null;
    }
  }
}
