package com.medtrack.booking.service;

import com.medtrack.booking.domain.AmbulanceEntity;
import com.medtrack.booking.domain.AmbulanceOfferEntity;
import com.medtrack.booking.domain.AmbulanceRequestEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.repo.AmbulanceOfferRepository;
import com.medtrack.booking.repo.AmbulanceRepository;
import com.medtrack.booking.repo.AmbulanceRequestRepository;
import com.medtrack.booking.repo.HospitalRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AmbulanceDispatchService {
  static final Duration OFFER_WINDOW = Duration.ofSeconds(45);
  static final Duration LOCATION_FRESH = Duration.ofMinutes(10);
  static final double SEARCH_RADIUS_KM = 30;
  static final double EXPANDED_RADIUS_KM = 80;

  @Value("${medtrack.ambulance.rate-per-km:25}")
  private double defaultRatePerKm;

  @Value("${medtrack.ambulance.avg-speed-kmh:35}")
  private double avgSpeedKmh;

  private final AmbulanceRepository ambulanceRepo;
  private final AmbulanceRequestRepository requestRepo;
  private final AmbulanceOfferRepository offerRepo;
  private final AmbulanceFcmService fcm;
  private final HospitalRepository hospitalRepo;

  public AmbulanceDispatchService(
      AmbulanceRepository ambulanceRepo,
      AmbulanceRequestRepository requestRepo,
      AmbulanceOfferRepository offerRepo,
      AmbulanceFcmService fcm,
      HospitalRepository hospitalRepo) {
    this.ambulanceRepo = ambulanceRepo;
    this.requestRepo = requestRepo;
    this.offerRepo = offerRepo;
    this.fcm = fcm;
    this.hospitalRepo = hospitalRepo;
  }

  @Transactional
  public Map<String, Object> registerAmbulance(Long hospitalId, Map<String, Object> body) {
    String name = text(body, "driverName");
    String mobile = digits(text(body, "mobileNumber"));
    if (name == null || name.isBlank() || mobile.length() < 10) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driverName and mobileNumber are required");
    }
    AmbulanceEntity row =
        ambulanceRepo
            .findByMobileNumber(mobile)
            .orElseGet(
                () -> {
                  AmbulanceEntity created = new AmbulanceEntity();
                  created.setId("amb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                  created.setCreatedAt(Instant.now());
                  created.setAvailabilityStatus("OFFLINE");
                  return created;
                });
    row.setHospitalId(hospitalId);
    row.setDriverName(name.trim());
    row.setMobileNumber(mobile);
    Double lat = toDouble(body.get("latitude"));
    Double lng = toDouble(body.get("longitude"));
    if (lat != null && lng != null) {
      row.setLatitude(lat);
      row.setLongitude(lng);
      row.setLastLocationUpdate(Instant.now());
      if ("OFFLINE".equalsIgnoreCase(row.getAvailabilityStatus())) {
        row.setAvailabilityStatus("AVAILABLE");
      }
    }
    row.setUpdatedAt(Instant.now());
    ambulanceRepo.save(row);
    return Map.of("message", "Ambulance saved", "ambulance", toAmbulance(row));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> pricingConfig(Long hospitalId) {
    double rate = resolveRatePerKm(hospitalId);
    return Map.of(
        "hospitalId", hospitalId == null ? "" : hospitalId,
        "ratePerKm", rate,
        "avgSpeedKmh", avgSpeedKmh,
        "currency", "INR");
  }

  @Transactional
  public Map<String, Object> updatePricing(Long hospitalId, Map<String, Object> body) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId is required");
    }
    Double rate = toDouble(body.get("ratePerKm"));
    if (rate == null || rate < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ratePerKm must be at least 1");
    }
    HospitalEntity hospital =
        hospitalRepo
            .findById(hospitalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
    hospital.setAmbulanceRatePerKm(rate);
    hospitalRepo.save(hospital);
    return Map.of("message", "Ambulance rate updated", "ratePerKm", rate, "hospitalId", hospitalId);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> quote(Double lat, Double lng, Long hospitalId) {
    if (lat == null || lng == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
    }
    double rate = resolveRatePerKm(hospitalId);
    Nearest nearest = findNearestAvailable(lat, lng);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("patientLatitude", lat);
    out.put("patientLongitude", lng);
    out.put("ratePerKm", rate);
    out.put("avgSpeedKmh", avgSpeedKmh);
    out.put("currency", "INR");
    if (nearest == null) {
      out.put("ambulanceAvailable", false);
      out.put("message", "No ambulance with recent GPS is available nearby right now.");
      return out;
    }
    double km = roundKm(nearest.km);
    out.put("ambulanceAvailable", true);
    out.put("distanceKm", km);
    out.put("estimatedPrice", roundPrice(km * rate));
    out.put("estimatedMinutes", estimateMinutes(km));
    out.put("ambulanceLatitude", nearest.ambulance.getLatitude());
    out.put("ambulanceLongitude", nearest.ambulance.getLongitude());
    out.put("driverName", nearest.ambulance.getDriverName());
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listAmbulances(Long hospitalId) {
    List<AmbulanceEntity> rows =
        hospitalId == null
            ? ambulanceRepo.findAllByOrderByDriverNameAsc()
            : ambulanceRepo.findByHospitalIdOrderByDriverNameAsc(hospitalId);
    return Map.of("count", rows.size(), "ambulances", rows.stream().map(this::toAmbulance).toList());
  }

  @Transactional
  public Map<String, Object> registerDevice(Map<String, Object> body) {
    AmbulanceEntity amb = requireByMobile(text(body, "mobileNumber"));
    String token = text(body, "fcmToken");
    if (token != null && !token.isBlank()) amb.setFcmToken(token.trim());
    applyLocation(amb, toDouble(body.get("latitude")), toDouble(body.get("longitude")), true);
    amb.setUpdatedAt(Instant.now());
    ambulanceRepo.save(amb);
    return Map.of("message", "Driver device registered", "ambulance", toAmbulance(amb));
  }

  @Transactional
  public Map<String, Object> updateLocation(Map<String, Object> body) {
    AmbulanceEntity amb = requireByMobile(text(body, "mobileNumber"));
    Double lat = toDouble(body.get("latitude"));
    Double lng = toDouble(body.get("longitude"));
    if (lat == null || lng == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
    }
    applyLocation(amb, lat, lng, false);
    amb.setUpdatedAt(Instant.now());
    ambulanceRepo.save(amb);
    return Map.of("ambulance", toAmbulance(amb));
  }

  @Transactional
  public Map<String, Object> requestAmbulance(Map<String, Object> body, String phone, String name, String patientId) {
    Double lat = toDouble(body.get("latitude"));
    Double lng = toDouble(body.get("longitude"));
    if (lat == null) lat = toDouble(body.get("patientLatitude"));
    if (lng == null) lng = toDouble(body.get("patientLongitude"));
    if (lat == null || lng == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient GPS latitude and longitude are required");
    }
    String patientPhone = digits(first(text(body, "patientPhone"), phone));
    if (patientPhone.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient phone is required");
    }
    AmbulanceRequestEntity req = new AmbulanceRequestEntity();
    req.setId("ambr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    req.setRequestNumber("AMB-" + System.currentTimeMillis());
    req.setHospitalId(toLong(body.get("hospitalId")));
    req.setPatientId(first(text(body, "patientId"), patientId));
    req.setPatientName(first(text(body, "patientName"), name));
    req.setPatientPhone(patientPhone);
    req.setPatientLatitude(lat);
    req.setPatientLongitude(lng);
    req.setStatus("REQUESTED");
    req.setMapsUrl(mapsUrl(lat, lng));
    req.setCreatedAt(Instant.now());
    applyQuoteToRequest(req, lat, lng);
    requestRepo.save(req);
    offerNext(req);
    return Map.of("message", "Ambulance requested", "request", toRequest(requireRequest(req.getId())));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> myRequests(String phone) {
    String digits = digits(phone);
    if (digits.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient phone is required");
    }
    List<Map<String, Object>> rows =
        requestRepo.findByPatientPhoneOrderByCreatedAtDesc(digits).stream().map(this::toRequest).toList();
    return Map.of("count", rows.size(), "requests", rows);
  }

  @Transactional
  public Map<String, Object> updatePatientLocation(String id, String phone, Map<String, Object> body) {
    AmbulanceRequestEntity req = requireRequest(id);
    assertPatient(req, phone);
    if (Set.of("COMPLETED", "CANCELLED").contains(req.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is closed");
    }
    Double lat = toDouble(body.get("latitude"));
    Double lng = toDouble(body.get("longitude"));
    if (lat == null || lng == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
    }
    req.setPatientLatitude(lat);
    req.setPatientLongitude(lng);
    req.setMapsUrl(mapsUrl(lat, lng));
    req.setUpdatedAt(Instant.now());
    refreshRequestPricing(req);
    requestRepo.save(req);
    return Map.of("request", toRequest(req));
  }

  @Transactional
  public Map<String, Object> cancel(String id, String phone) {
    AmbulanceRequestEntity req = requireRequest(id);
    assertPatient(req, phone);
    if (Set.of("COMPLETED", "CANCELLED").contains(req.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is already closed");
    }
    req.setStatus("CANCELLED");
    req.setCancelledAt(Instant.now());
    req.setUpdatedAt(Instant.now());
    requestRepo.save(req);
    releaseAmbulance(req.getAmbulanceId());
    expireOpenOffers(req.getId(), "EXPIRED");
    return Map.of("request", toRequest(req));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> driverOffers(String mobile) {
    AmbulanceEntity amb = requireByMobile(mobile);
    List<Map<String, Object>> offers =
        offerRepo.findByAmbulanceIdAndStatusOrderByOfferedAtDesc(amb.getId(), "OFFERED").stream()
            .filter(o -> o.getDeadlineAt() != null && o.getDeadlineAt().isAfter(Instant.now()))
            .map(o -> offerMap(o, requireRequest(o.getRequestId())))
            .toList();
    return Map.of("count", offers.size(), "offers", offers, "ambulance", toAmbulance(amb));
  }

  @Transactional
  public Map<String, Object> accept(String requestId, String mobile) {
    AmbulanceEntity amb = requireByMobile(mobile);
    AmbulanceRequestEntity req = requireRequest(requestId);
    if (!Set.of("REQUESTED", "OFFERED").contains(req.getStatus()) || req.getAmbulanceId() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already assigned");
    }
    AmbulanceOfferEntity offer =
        offerRepo
            .findFirstByRequestIdAndAmbulanceIdAndStatus(requestId, amb.getId(), "OFFERED")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No open offer for this driver"));
    if (offer.getDeadlineAt().isBefore(Instant.now())) {
      offer.setStatus("EXPIRED");
      offer.setRespondedAt(Instant.now());
      offerRepo.save(offer);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer expired");
    }
    offer.setStatus("ACCEPTED");
    offer.setRespondedAt(Instant.now());
    offerRepo.save(offer);
    req.setAmbulanceId(amb.getId());
    req.setStatus("ACCEPTED");
    req.setAcceptedAt(Instant.now());
    req.setUpdatedAt(Instant.now());
    refreshRequestPricing(req);
    requestRepo.save(req);
    amb.setAvailabilityStatus("BUSY");
    amb.setUpdatedAt(Instant.now());
    ambulanceRepo.save(amb);
    expireOpenOffers(requestId, "EXPIRED");
    return Map.of("message", "Request accepted", "request", toRequest(req), "mapsUrl", req.getMapsUrl());
  }

  @Transactional
  public Map<String, Object> reject(String requestId, String mobile) {
    AmbulanceEntity amb = requireByMobile(mobile);
    AmbulanceOfferEntity offer =
        offerRepo
            .findFirstByRequestIdAndAmbulanceIdAndStatus(requestId, amb.getId(), "OFFERED")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
    offer.setStatus("REJECTED");
    offer.setRespondedAt(Instant.now());
    offerRepo.save(offer);
    AmbulanceRequestEntity req = requireRequest(requestId);
    if (Set.of("REQUESTED", "OFFERED").contains(req.getStatus())) {
      offerNext(req);
    }
    return Map.of("message", "Offer declined", "request", toRequest(requireRequest(requestId)));
  }

  @Transactional
  public Map<String, Object> markStatus(String requestId, String mobile, String status) {
    AmbulanceEntity amb = requireByMobile(mobile);
    AmbulanceRequestEntity req = requireRequest(requestId);
    if (req.getAmbulanceId() == null || !req.getAmbulanceId().equals(amb.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request is not assigned to this ambulance");
    }
    if ("ARRIVED".equals(status)) {
      if (!"ACCEPTED".equals(req.getStatus())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request must be accepted first");
      }
      req.setStatus("ARRIVED");
      req.setArrivedAt(Instant.now());
    } else if ("COMPLETED".equals(status)) {
      if (!Set.of("ACCEPTED", "ARRIVED").contains(req.getStatus())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request cannot be completed");
      }
      req.setStatus("COMPLETED");
      req.setCompletedAt(Instant.now());
      amb.setAvailabilityStatus("AVAILABLE");
      amb.setUpdatedAt(Instant.now());
      ambulanceRepo.save(amb);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ARRIVED or COMPLETED");
    }
    req.setUpdatedAt(Instant.now());
    requestRepo.save(req);
    return Map.of("request", toRequest(req));
  }

  @Scheduled(fixedDelay = 15000)
  @Transactional
  public void expireOffers() {
    List<AmbulanceOfferEntity> due =
        offerRepo.findByStatusAndDeadlineAtBefore("OFFERED", Instant.now());
    for (AmbulanceOfferEntity offer : due) {
      offer.setStatus("EXPIRED");
      offer.setRespondedAt(Instant.now());
      offerRepo.save(offer);
      requestRepo
          .findById(offer.getRequestId())
          .ifPresent(
              req -> {
                if (Set.of("REQUESTED", "OFFERED").contains(req.getStatus()) && req.getAmbulanceId() == null) {
                  offerNext(req);
                }
              });
    }
  }

  private void offerNext(AmbulanceRequestEntity req) {
    Set<String> already =
        offerRepo.findByRequestId(req.getId()).stream()
            .map(AmbulanceOfferEntity::getAmbulanceId)
            .collect(java.util.stream.Collectors.toSet());
    Instant freshAfter = Instant.now().minus(LOCATION_FRESH);
    List<AmbulanceEntity> available =
        ambulanceRepo.findByAvailabilityStatus("AVAILABLE").stream()
            .filter(a -> !already.contains(a.getId()))
            .filter(a -> a.getLatitude() != null && a.getLongitude() != null)
            .filter(a -> a.getLastLocationUpdate() != null && a.getLastLocationUpdate().isAfter(freshAfter))
            .toList();
    record Candidate(AmbulanceEntity ambulance, double km) {}
    List<Candidate> ranked =
        available.stream()
            .map(a -> new Candidate(a, haversine(req.getPatientLatitude(), req.getPatientLongitude(), a.getLatitude(), a.getLongitude())))
            .sorted(Comparator.comparingDouble(Candidate::km))
            .toList();
    boolean expanded = offerRepo.findByRequestId(req.getId()).size() >= 3;
    double radius = expanded ? EXPANDED_RADIUS_KM : SEARCH_RADIUS_KM;
    Candidate next = ranked.stream().filter(c -> c.km <= radius).findFirst().orElse(null);
    if (next == null && expanded) {
      next = ranked.stream().findFirst().orElse(null);
    }
    if (next == null) {
      req.setStatus("REQUESTED");
      req.setUpdatedAt(Instant.now());
      requestRepo.save(req);
      return;
    }
    AmbulanceOfferEntity offer = new AmbulanceOfferEntity();
    offer.setId("ambo-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    offer.setRequestId(req.getId());
    offer.setAmbulanceId(next.ambulance.getId());
    offer.setStatus("OFFERED");
    offer.setDistanceKm(roundKm(next.km));
    offer.setOfferedAt(Instant.now());
    offer.setDeadlineAt(Instant.now().plus(OFFER_WINDOW));
    offerRepo.save(offer);
    req.setStatus("OFFERED");
    req.setUpdatedAt(Instant.now());
    applyPricing(req, offer.getDistanceKm());
    requestRepo.save(req);
    fcm.push(
        next.ambulance.getFcmToken(),
        "MediMitra — Emergency request",
        "A patient near you needs an ambulance. Pickup is "
            + offer.getDistanceKm()
            + " km away. Accept within 45 seconds.",
        req.getId(),
        req.getPatientLatitude(),
        req.getPatientLongitude());
  }

  private void expireOpenOffers(String requestId, String status) {
    for (AmbulanceOfferEntity offer : offerRepo.findByRequestId(requestId)) {
      if ("OFFERED".equals(offer.getStatus())) {
        offer.setStatus(status);
        offer.setRespondedAt(Instant.now());
        offerRepo.save(offer);
      }
    }
  }

  private void releaseAmbulance(String ambulanceId) {
    if (ambulanceId == null) return;
    ambulanceRepo
        .findById(ambulanceId)
        .ifPresent(
            amb -> {
              if ("BUSY".equalsIgnoreCase(amb.getAvailabilityStatus())) {
                amb.setAvailabilityStatus("AVAILABLE");
                amb.setUpdatedAt(Instant.now());
                ambulanceRepo.save(amb);
              }
            });
  }

  private void applyLocation(AmbulanceEntity amb, Double lat, Double lng, boolean fromDevice) {
    if (lat == null || lng == null) {
      if (fromDevice && "OFFLINE".equalsIgnoreCase(amb.getAvailabilityStatus())) {
        amb.setAvailabilityStatus("AVAILABLE");
      }
      return;
    }
    amb.setLatitude(lat);
    amb.setLongitude(lng);
    amb.setLastLocationUpdate(Instant.now());
    if (!"BUSY".equalsIgnoreCase(amb.getAvailabilityStatus())) {
      amb.setAvailabilityStatus("AVAILABLE");
    }
  }

  private AmbulanceEntity requireByMobile(String mobile) {
    String digits = digits(mobile);
    return ambulanceRepo
        .findByMobileNumber(digits)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance is not registered for this mobile"));
  }

  private AmbulanceRequestEntity requireRequest(String id) {
    return requestRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance request not found"));
  }

  private void assertPatient(AmbulanceRequestEntity req, String phone) {
    if (!digits(req.getPatientPhone()).equals(digits(phone))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your request");
    }
  }

  private Map<String, Object> toAmbulance(AmbulanceEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("hospitalId", a.getHospitalId());
    m.put("driverName", a.getDriverName());
    m.put("mobileNumber", a.getMobileNumber());
    m.put("latitude", a.getLatitude());
    m.put("longitude", a.getLongitude());
    m.put("availabilityStatus", a.getAvailabilityStatus());
    m.put("fcmRegistered", a.getFcmToken() != null && !a.getFcmToken().isBlank());
    m.put("lastLocationUpdate", a.getLastLocationUpdate() == null ? null : a.getLastLocationUpdate().toString());
    return m;
  }

  private Map<String, Object> toRequest(AmbulanceRequestEntity r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("requestNumber", r.getRequestNumber());
    m.put("patientName", r.getPatientName());
    m.put("patientPhone", r.getPatientPhone());
    m.put("patientLatitude", r.getPatientLatitude());
    m.put("patientLongitude", r.getPatientLongitude());
    m.put("ambulanceId", r.getAmbulanceId());
    m.put("status", r.getStatus());
    m.put("mapsUrl", r.getMapsUrl());
    m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    m.put("acceptedAt", r.getAcceptedAt() == null ? null : r.getAcceptedAt().toString());
    m.put("ratePerKm", resolveRatePerKm(r.getHospitalId()));
    if (r.getDistanceKm() != null) m.put("distanceKm", r.getDistanceKm());
    if (r.getEstimatedPrice() != null) m.put("estimatedPrice", r.getEstimatedPrice());
    if (r.getEstimatedMinutes() != null) m.put("estimatedMinutes", r.getEstimatedMinutes());
    if (r.getAmbulanceId() != null) {
      ambulanceRepo.findById(r.getAmbulanceId()).ifPresent(a -> {
        m.put("driverName", a.getDriverName());
        m.put("driverMobile", a.getMobileNumber());
        m.put("ambulanceLatitude", a.getLatitude());
        m.put("ambulanceLongitude", a.getLongitude());
        if (a.getLatitude() != null && a.getLongitude() != null) {
          double liveKm =
              roundKm(
                  haversine(
                      r.getPatientLatitude(),
                      r.getPatientLongitude(),
                      a.getLatitude(),
                      a.getLongitude()));
          m.put("distanceKm", liveKm);
          double rate = resolveRatePerKm(r.getHospitalId());
          m.put("estimatedPrice", roundPrice(liveKm * rate));
          m.put("estimatedMinutes", estimateMinutes(liveKm));
        }
      });
    } else {
      offerRepo.findByRequestId(r.getId()).stream()
          .filter(o -> "OFFERED".equals(o.getStatus()))
          .findFirst()
          .ifPresent(
              o -> {
                m.put("offeredDistanceKm", o.getDistanceKm());
                m.put("offerDeadlineAt", o.getDeadlineAt().toString());
                if (o.getDistanceKm() != null) {
                  m.put("distanceKm", o.getDistanceKm());
                  double rate = resolveRatePerKm(r.getHospitalId());
                  m.put("estimatedPrice", roundPrice(o.getDistanceKm() * rate));
                  m.put("estimatedMinutes", estimateMinutes(o.getDistanceKm()));
                }
                ambulanceRepo
                    .findById(o.getAmbulanceId())
                    .ifPresent(
                        a -> {
                          m.put("ambulanceLatitude", a.getLatitude());
                          m.put("ambulanceLongitude", a.getLongitude());
                          m.put("driverName", a.getDriverName());
                        });
              });
    }
    return m;
  }

  private record Nearest(AmbulanceEntity ambulance, double km) {}

  private Nearest findNearestAvailable(double lat, double lng) {
    Instant freshAfter = Instant.now().minus(LOCATION_FRESH);
    return ambulanceRepo.findByAvailabilityStatus("AVAILABLE").stream()
        .filter(a -> a.getLatitude() != null && a.getLongitude() != null)
        .filter(a -> a.getLastLocationUpdate() != null && a.getLastLocationUpdate().isAfter(freshAfter))
        .map(a -> new Nearest(a, haversine(lat, lng, a.getLatitude(), a.getLongitude())))
        .min(Comparator.comparingDouble(Nearest::km))
        .orElse(null);
  }

  private void applyQuoteToRequest(AmbulanceRequestEntity req, double lat, double lng) {
    Nearest nearest = findNearestAvailable(lat, lng);
    if (nearest != null) {
      applyPricing(req, roundKm(nearest.km));
    } else {
      req.setRatePerKm(resolveRatePerKm(req.getHospitalId()));
    }
  }

  private void refreshRequestPricing(AmbulanceRequestEntity req) {
    if (req.getAmbulanceId() != null) {
      ambulanceRepo
          .findById(req.getAmbulanceId())
          .ifPresent(
              a -> {
                if (a.getLatitude() != null && a.getLongitude() != null) {
                  applyPricing(
                      req,
                      roundKm(
                          haversine(
                              req.getPatientLatitude(),
                              req.getPatientLongitude(),
                              a.getLatitude(),
                              a.getLongitude())));
                }
              });
      return;
    }
    applyQuoteToRequest(req, req.getPatientLatitude(), req.getPatientLongitude());
  }

  private void applyPricing(AmbulanceRequestEntity req, double km) {
    double rate = resolveRatePerKm(req.getHospitalId());
    req.setDistanceKm(km);
    req.setRatePerKm(rate);
    req.setEstimatedPrice(roundPrice(km * rate));
    req.setEstimatedMinutes(estimateMinutes(km));
  }

  private double resolveRatePerKm(Long hospitalId) {
    if (hospitalId == null) return defaultRatePerKm;
    return hospitalRepo
        .findById(hospitalId)
        .map(HospitalEntity::getAmbulanceRatePerKm)
        .filter(r -> r != null && r >= 1)
        .orElse(defaultRatePerKm);
  }

  private int estimateMinutes(double km) {
    if (km <= 0 || avgSpeedKmh <= 0) return 0;
    return (int) Math.max(1, Math.ceil((km / avgSpeedKmh) * 60.0));
  }

  private static double roundKm(double km) {
    return Math.round(km * 100.0) / 100.0;
  }

  private static double roundPrice(double price) {
    return Math.round(price * 100.0) / 100.0;
  }

  private Map<String, Object> offerMap(AmbulanceOfferEntity offer, AmbulanceRequestEntity req) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("offerId", offer.getId());
    m.put("requestId", req.getId());
    m.put("requestNumber", req.getRequestNumber());
    m.put("patientName", req.getPatientName());
    m.put("patientPhone", req.getPatientPhone());
    m.put("patientLatitude", req.getPatientLatitude());
    m.put("patientLongitude", req.getPatientLongitude());
    m.put("distanceKm", offer.getDistanceKm());
    m.put("deadlineAt", offer.getDeadlineAt().toString());
    m.put("mapsUrl", req.getMapsUrl());
    m.put("status", offer.getStatus());
    return m;
  }

  static double haversine(double lat1, double lng1, double lat2, double lng2) {
    double r = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLng / 2), 2);
    return r * 2 * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  private static String mapsUrl(double lat, double lng) {
    return "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng;
  }

  private static String text(Map<String, Object> body, String key) {
    if (body == null || body.get(key) == null) return null;
    String s = String.valueOf(body.get(key)).trim();
    return s.isEmpty() ? null : s;
  }

  private static String first(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
  }

  private static String digits(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D+", "");
  }

  private static Double toDouble(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(String.valueOf(raw).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Long toLong(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(String.valueOf(raw).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
