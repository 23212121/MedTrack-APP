package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.*;
import com.medtrack.hrm.repo.*;
import com.medtrack.hrm.web.AuditContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrmAppService {
  private final AuditContext audit;
  private final HrmDepartmentRepository departments;
  private final DesignationRepository designations;
  private final ShiftRepository shifts;
  private final EmployeeRepository employees;
  private final EmployeeDocumentRepository documents;
  private final AttendanceRepository attendance;
  private final LeaveTypeRepository leaveTypes;
  private final LeaveRequestRepository leaveRequests;
  private final LeaveApplicationRepository leaveApplications;
  private final LeaveBalanceRepository leaveBalances;
  private final HolidayRepository holidays;
  private final SalaryStructureRepository salaries;
  private final PayslipRepository payslips;
  private final PerformanceReviewRepository reviews;
  private final CandidateRepository candidates;
  private final InterviewRepository interviews;
  private final OnboardingRepository onboardings;
  private final AssetRepository assets;
  private final TrainingRepository trainings;
  private final ResignationRepository resignations;

  public HrmAppService(
      AuditContext audit,
      HrmDepartmentRepository departments,
      DesignationRepository designations,
      ShiftRepository shifts,
      EmployeeRepository employees,
      EmployeeDocumentRepository documents,
      AttendanceRepository attendance,
      LeaveTypeRepository leaveTypes,
      LeaveRequestRepository leaveRequests,
      LeaveApplicationRepository leaveApplications,
      LeaveBalanceRepository leaveBalances,
      HolidayRepository holidays,
      SalaryStructureRepository salaries,
      PayslipRepository payslips,
      PerformanceReviewRepository reviews,
      CandidateRepository candidates,
      InterviewRepository interviews,
      OnboardingRepository onboardings,
      AssetRepository assets,
      TrainingRepository trainings,
      ResignationRepository resignations) {
    this.audit = audit;
    this.departments = departments;
    this.designations = designations;
    this.shifts = shifts;
    this.employees = employees;
    this.documents = documents;
    this.attendance = attendance;
    this.leaveTypes = leaveTypes;
    this.leaveRequests = leaveRequests;
    this.leaveApplications = leaveApplications;
    this.leaveBalances = leaveBalances;
    this.holidays = holidays;
    this.salaries = salaries;
    this.payslips = payslips;
    this.reviews = reviews;
    this.candidates = candidates;
    this.interviews = interviews;
    this.onboardings = onboardings;
    this.assets = assets;
    this.trainings = trainings;
    this.resignations = resignations;
  }

  private void applyAudit(AuditableEntity e) {
    e.touchAudit(audit.doctorId(), audit.user());
  }

  private <T> T or404(Optional<T> o, String msg) {
    return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
  }

  // —— Dashboard ——
  public Map<String, Object> dashboard() {
    String d = audit.doctorId();
    LocalDate today = LocalDate.now();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("totalEmployees", employees.countByDoctorId(d));
    m.put("activeEmployees", employees.countByDoctorIdAndEmploymentStatus(d, "Active"));
    m.put("doctors", employees.countByDoctorIdAndEmployeeType(d, "Doctor"));
    m.put("nurses", employees.countByDoctorIdAndEmployeeType(d, "Nurse"));
    m.put("staffOnLeave", leaveRequests.countByDoctorIdAndStatus(d, "Approved"));
    m.put("todayPresent", attendance.countByDoctorIdAndAttendanceDateAndStatus(d, today, "Present"));
    m.put("todayAbsent", attendance.countByDoctorIdAndAttendanceDateAndStatus(d, today, "Absent"));
    m.put("pendingLeaveRequests", leaveRequests.countByDoctorIdAndStatus(d, "Pending"));
    m.put(
        "upcomingBirthdays",
        employees.findByDoctorIdOrderByEmployeeIdAsc(d).stream()
            .filter(e -> e.getDateOfBirth() != null)
            .filter(
                e -> {
                  LocalDate dob = e.getDateOfBirth().withYear(today.getYear());
                  long days = ChronoUnit.DAYS.between(today, dob);
                  if (days < 0) days = ChronoUnit.DAYS.between(today, dob.plusYears(1));
                  return days >= 0 && days <= 30;
                })
            .limit(10)
            .map(
                e ->
                    Map.of(
                        "employeeId", e.getEmployeeId(),
                        "name", e.getFullName(),
                        "dateOfBirth", e.getDateOfBirth().toString()))
            .toList());
    m.put(
        "upcomingHolidays",
        holidays.findByDoctorIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(d, today).stream()
            .limit(8)
            .toList());
    BigDecimal payroll =
        payslips.findByDoctorIdOrderByPayYearDescPayMonthDesc(d).stream()
            .limit(50)
            .map(PayslipEntity::getNetSalary)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    m.put("payrollSummaryNet", payroll);
    return m;
  }

  // —— Departments ——
  public List<DepartmentEntity> listDepartments() {
    return departments.findByDoctorIdOrderByNameAsc(audit.doctorId());
  }

  @Transactional
  public DepartmentEntity saveDepartment(DepartmentEntity body) {
    if (body.getId() != null) {
      DepartmentEntity existing = or404(departments.findById(body.getId()), "Department not found");
      existing.setName(body.getName());
      existing.setDepartmentHead(body.getDepartmentHead());
      existing.setDescription(body.getDescription());
      applyAudit(existing);
      return departments.save(existing);
    }
    applyAudit(body);
    return departments.save(body);
  }

  @Transactional
  public void deleteDepartment(String id) {
    departments.deleteById(id);
  }

  // —— Designations ——
  public List<DesignationEntity> listDesignations() {
    return designations.findByDoctorIdOrderByNameAsc(audit.doctorId());
  }

  @Transactional
  public DesignationEntity saveDesignation(DesignationEntity body) {
    if (body.getDepartmentId() != null) {
      departments
          .findById(body.getDepartmentId())
          .ifPresent(dep -> body.setDepartmentName(dep.getName()));
    }
    if (body.getId() != null) {
      DesignationEntity existing = or404(designations.findById(body.getId()), "Designation not found");
      existing.setName(body.getName());
      existing.setDepartmentId(body.getDepartmentId());
      existing.setDepartmentName(body.getDepartmentName());
      existing.setSalaryGrade(body.getSalaryGrade());
      applyAudit(existing);
      return designations.save(existing);
    }
    applyAudit(body);
    return designations.save(body);
  }

  @Transactional
  public void deleteDesignation(String id) {
    designations.deleteById(id);
  }

  // —— Shifts ——
  public List<ShiftEntity> listShifts() {
    return shifts.findByDoctorIdOrderByNameAsc(audit.doctorId());
  }

  @Transactional
  public ShiftEntity saveShift(ShiftEntity body) {
    if (body.getId() != null) {
      ShiftEntity existing = or404(shifts.findById(body.getId()), "Shift not found");
      existing.setName(body.getName());
      existing.setStartTime(body.getStartTime());
      existing.setEndTime(body.getEndTime());
      existing.setBreakDurationMinutes(body.getBreakDurationMinutes());
      applyAudit(existing);
      return shifts.save(existing);
    }
    applyAudit(body);
    return shifts.save(body);
  }

  @Transactional
  public void deleteShift(String id) {
    shifts.deleteById(id);
  }

  // —— Employees ——
  public List<EmployeeEntity> listEmployees() {
    return employees.findByDoctorIdOrderByEmployeeIdAsc(audit.doctorId());
  }

  public List<Map<String, Object>> listRosterEmployees() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (EmployeeEntity e : employees.findAll()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("employeeId", e.getEmployeeId());
      row.put("firstName", e.getFirstName());
      row.put("lastName", e.getLastName());
      row.put("name", e.getFullName());
      row.put("employeeType", e.getEmployeeType());
      row.put("designation", e.getDesignation());
      row.put("department", e.getDepartment());
      row.put("doctorLinkId", e.getDoctorLinkId());
      out.add(row);
    }
    return out;
  }

  public EmployeeEntity getEmployee(String id) {
    return or404(employees.findById(id), "Employee not found");
  }

  @Transactional
  public EmployeeEntity saveEmployee(EmployeeEntity body) {
    if (body.getDepartmentId() != null) {
      departments.findById(body.getDepartmentId()).ifPresent(d -> body.setDepartment(d.getName()));
    }
    if (body.getDesignationId() != null) {
      designations.findById(body.getDesignationId()).ifPresent(d -> body.setDesignation(d.getName()));
    }
    if (body.getShiftId() != null) {
      shifts.findById(body.getShiftId()).ifPresent(s -> body.setShift(s.getName()));
    }
    if (body.getId() != null) {
      EmployeeEntity existing = or404(employees.findById(body.getId()), "Employee not found");
      copyEmployee(body, existing);
      applyAudit(existing);
      return employees.save(existing);
    }
    if (body.getEmployeeId() == null || body.getEmployeeId().isBlank()) {
      long n = employees.count() + 1;
      body.setEmployeeId("EMP-" + String.format("%04d", n));
    }
    if ("Doctor".equalsIgnoreCase(body.getEmployeeType())
        && (body.getDoctorLinkId() == null || body.getDoctorLinkId().isBlank())) {
      body.setDoctorLinkId("DOC-" + body.getEmployeeId());
    }
    if (body.getEmploymentStatus() == null) body.setEmploymentStatus("Active");
    applyAudit(body);
    EmployeeEntity saved = employees.save(body);
    OnboardingEntity ob = new OnboardingEntity();
    ob.setEmployeePk(saved.getId());
    ob.setEmployeeId(saved.getEmployeeId());
    ob.setEmployeeName(saved.getFullName());
    ob.setDepartmentAssigned(saved.getDepartmentId() != null);
    ob.setShiftAssigned(saved.getShiftId() != null);
    applyAudit(ob);
    onboardings.save(ob);
    return saved;
  }

  private void copyEmployee(EmployeeEntity src, EmployeeEntity dest) {
    dest.setFirstName(src.getFirstName());
    dest.setLastName(src.getLastName());
    dest.setGender(src.getGender());
    dest.setDateOfBirth(src.getDateOfBirth());
    dest.setBloodGroup(src.getBloodGroup());
    dest.setMaritalStatus(src.getMaritalStatus());
    dest.setPhoto(src.getPhoto());
    dest.setMobileNumber(src.getMobileNumber());
    dest.setAlternateNumber(src.getAlternateNumber());
    dest.setEmail(src.getEmail());
    dest.setAddress(src.getAddress());
    dest.setCity(src.getCity());
    dest.setState(src.getState());
    dest.setPinCode(src.getPinCode());
    dest.setEmployeeType(src.getEmployeeType());
    dest.setDepartmentId(src.getDepartmentId());
    dest.setDepartment(src.getDepartment());
    dest.setDesignationId(src.getDesignationId());
    dest.setDesignation(src.getDesignation());
    dest.setReportingManager(src.getReportingManager());
    dest.setJoiningDate(src.getJoiningDate());
    dest.setShiftId(src.getShiftId());
    dest.setShift(src.getShift());
    dest.setEmploymentStatus(src.getEmploymentStatus());
    dest.setDoctorLinkId(src.getDoctorLinkId());
  }

  @Transactional
  public void deleteEmployee(String id) {
    employees.deleteById(id);
  }

  // —— Documents ——
  public List<EmployeeDocumentEntity> listDocuments() {
    return documents.findByDoctorIdOrderByCreationDateDesc(audit.doctorId());
  }

  @Transactional
  public EmployeeDocumentEntity saveDocument(EmployeeDocumentEntity body) {
    applyAudit(body);
    return documents.save(body);
  }

  // —— Attendance ——
  public List<AttendanceEntity> listAttendance() {
    return attendance.findByDoctorIdOrderByAttendanceDateDesc(audit.doctorId());
  }

  @Transactional
  public AttendanceEntity saveAttendance(AttendanceEntity body) {
    if (body.getInTime() != null && body.getOutTime() != null) {
      long mins = ChronoUnit.MINUTES.between(body.getInTime(), body.getOutTime());
      if (mins < 0) mins += 24 * 60;
      body.setTotalHours(
          BigDecimal.valueOf(mins).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
    }
    if (body.getId() != null) {
      AttendanceEntity existing = or404(attendance.findById(body.getId()), "Attendance not found");
      existing.setInTime(body.getInTime());
      existing.setOutTime(body.getOutTime());
      existing.setTotalHours(body.getTotalHours());
      existing.setStatus(body.getStatus());
      existing.setSource(body.getSource());
      existing.setCorrectionRequested(body.getCorrectionRequested());
      existing.setCorrectionReason(body.getCorrectionReason());
      applyAudit(existing);
      return attendance.save(existing);
    }
    if (body.getSource() == null) body.setSource("Manual");
    applyAudit(body);
    return attendance.save(body);
  }

  /** Today's office in/out plus recent logs for the logged-in doctor. */
  @Transactional
  public Map<String, Object> attendanceBoard() {
    EmployeeEntity emp = ensureSelfEmployeeWithBalances();
    LocalDate today = LocalDate.now();
    List<AttendanceEntity> rows =
        attendance.findByDoctorIdOrderByAttendanceDateDesc(audit.doctorId()).stream()
            .filter(a -> emp.getEmployeeId().equals(a.getEmployeeId()))
            .toList();
    AttendanceEntity todayRow =
        rows.stream()
            .filter(a -> today.equals(a.getAttendanceDate()))
            .findFirst()
            .orElse(null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("today", today.toString());
    out.put("employeeId", emp.getEmployeeId());
    out.put("employeeName", emp.getFullName());
    out.put(
        "inTime",
        todayRow == null || todayRow.getInTime() == null ? null : todayRow.getInTime().toString());
    out.put(
        "outTime",
        todayRow == null || todayRow.getOutTime() == null ? null : todayRow.getOutTime().toString());
    out.put("status", todayRow == null ? null : todayRow.getStatus());
    out.put("totalHours", todayRow == null ? null : todayRow.getTotalHours());
    out.put("canClockIn", todayRow == null || todayRow.getInTime() == null);
    out.put(
        "canClockOut",
        todayRow != null && todayRow.getInTime() != null && todayRow.getOutTime() == null);
    out.put("logs", rows);
    return out;
  }

  @Transactional
  public AttendanceEntity clockIn() {
    EmployeeEntity emp = ensureSelfEmployeeWithBalances();
    LocalDate today = LocalDate.now();
    AttendanceEntity row =
        attendance.findByDoctorIdAndAttendanceDate(audit.doctorId(), today).stream()
            .filter(a -> emp.getEmployeeId().equals(a.getEmployeeId()))
            .findFirst()
            .orElse(null);
    if (row != null && row.getInTime() != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already clocked in today");
    }
    if (row == null) {
      row = new AttendanceEntity();
      row.setEmployeePk(emp.getId());
      row.setEmployeeId(emp.getEmployeeId());
      row.setEmployeeName(emp.getFullName());
      row.setAttendanceDate(today);
      row.setStatus("Present");
      row.setSource("Web");
    }
    row.setInTime(LocalTime.now().withNano(0));
    applyAudit(row);
    return attendance.save(row);
  }

  @Transactional
  public AttendanceEntity clockOut() {
    EmployeeEntity emp = ensureSelfEmployeeWithBalances();
    LocalDate today = LocalDate.now();
    AttendanceEntity row =
        attendance.findByDoctorIdAndAttendanceDate(audit.doctorId(), today).stream()
            .filter(a -> emp.getEmployeeId().equals(a.getEmployeeId()))
            .findFirst()
            .orElse(null);
    if (row == null || row.getInTime() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clock in first");
    }
    if (row.getOutTime() != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already clocked out today");
    }
    LocalTime outTime = LocalTime.now().withNano(0);
    row.setOutTime(outTime);
    long mins = ChronoUnit.MINUTES.between(row.getInTime(), outTime);
    if (mins < 0) mins += 24 * 60;
    row.setTotalHours(
        BigDecimal.valueOf(mins).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
    row.setStatus("Present");
    applyAudit(row);
    return attendance.save(row);
  }

  // —— Leave ——
  public List<LeaveTypeEntity> listLeaveTypes() {
    return leaveTypes.findByDoctorIdOrderByNameAsc(audit.doctorId());
  }

  @Transactional
  public LeaveTypeEntity saveLeaveType(LeaveTypeEntity body) {
    applyAudit(body);
    return leaveTypes.save(body);
  }

  public List<LeaveRequestEntity> listLeaveRequests(String status) {
    if (status != null && !status.isBlank()) {
      return leaveRequests.findByDoctorIdAndStatus(audit.doctorId(), status);
    }
    return leaveRequests.findByDoctorIdOrderByCreationDateDesc(audit.doctorId());
  }

  @Transactional
  public LeaveRequestEntity saveLeaveRequest(LeaveRequestEntity body) {
    if (body.getStatus() == null) body.setStatus("Pending");
    applyAudit(body);
    return leaveRequests.save(body);
  }

  @Transactional
  public LeaveRequestEntity decideLeave(String id, String status, String remarks) {
    LeaveRequestEntity req = or404(leaveRequests.findById(id), "Leave request not found");
    req.setStatus(status);
    req.setApproverRemarks(remarks);
    applyAudit(req);
    if ("Approved".equalsIgnoreCase(status)) {
      leaveBalances.findByEmployeeId(req.getEmployeeId()).stream()
          .filter(b -> Objects.equals(b.getLeaveTypeId(), req.getLeaveTypeId()))
          .findFirst()
          .ifPresent(
              b -> {
                long days = ChronoUnit.DAYS.between(req.getFromDate(), req.getToDate()) + 1;
                b.setUsedLeave(b.getUsedLeave() + (int) days);
                b.setRemainingLeave(Math.max(0, b.getTotalLeave() - b.getUsedLeave()));
                applyAudit(b);
                leaveBalances.save(b);
              });
    }
    return leaveRequests.save(req);
  }

  public List<LeaveBalanceEntity> listLeaveBalances() {
    return leaveBalances.findByDoctorIdOrderByEmployeeIdAsc(audit.doctorId());
  }

  @Transactional
  public LeaveBalanceEntity saveLeaveBalance(LeaveBalanceEntity body) {
    if (body.getRemainingLeave() == null) {
      int total = body.getTotalLeave() == null ? 0 : body.getTotalLeave();
      int used = body.getUsedLeave() == null ? 0 : body.getUsedLeave();
      body.setRemainingLeave(Math.max(0, total - used));
    }
    applyAudit(body);
    return leaveBalances.save(body);
  }

  /** Ensure catalog leave types exist for this doctor tenant. */
  @Transactional
  public void ensureLeaveCatalog() {
    String d = audit.doctorId();
    if (!leaveTypes.findByDoctorIdOrderByNameAsc(d).isEmpty()) {
      return;
    }
    // Default entitlements — override per doctor via PUT /api/hrm/leave/entitlements
    String[][] leaveRows = {
      {"Casual Leave", "12"},
      {"Sick Leave", "10"},
      {"Earned Leave", "15"},
      {"Paternity Leave", "15"}
    };
    for (String[] row : leaveRows) {
      LeaveTypeEntity e = new LeaveTypeEntity();
      e.setName(row[0]);
      e.setAnnualQuota(Integer.parseInt(row[1]));
      applyAudit(e);
      leaveTypes.save(e);
    }
  }

  /**
   * Set exactly which leave types this doctor has. Balance cards on the Leave page
   * follow this list. Preserves used days when the same type name already exists.
   */
  @Transactional
  public Map<String, Object> setDoctorLeaveEntitlements(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entitlements list required");
    }
    EmployeeEntity emp = ensureSelfEmployeeWithBalances();
    String doctorId = audit.doctorId();

    Map<String, Integer> usedByName = new HashMap<>();
    for (LeaveBalanceEntity b : leaveBalances.findByEmployeeId(emp.getEmployeeId())) {
      if (doctorId.equals(b.getDoctorId()) && b.getLeaveType() != null) {
        usedByName.put(b.getLeaveType().toLowerCase(Locale.ROOT), nz(b.getUsedLeave()));
      }
    }

    for (LeaveBalanceEntity b : leaveBalances.findByEmployeeId(emp.getEmployeeId())) {
      if (doctorId.equals(b.getDoctorId())) {
        leaveBalances.delete(b);
      }
    }
    for (LeaveTypeEntity t : leaveTypes.findByDoctorIdOrderByNameAsc(doctorId)) {
      leaveTypes.delete(t);
    }

    List<Map<String, Object>> created = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String name = str(row.get("name"));
      if (name == null || name.isBlank()) {
        continue;
      }
      int quota = 0;
      Object q = row.get("annualQuota");
      if (q == null) q = row.get("total");
      if (q != null) {
        try {
          quota = (int) Double.parseDouble(String.valueOf(q));
        } catch (NumberFormatException ignored) {
          quota = 0;
        }
      }
      LeaveTypeEntity t = new LeaveTypeEntity();
      t.setName(name.trim());
      t.setAnnualQuota(quota);
      applyAudit(t);
      t = leaveTypes.save(t);

      int used = usedByName.getOrDefault(name.trim().toLowerCase(Locale.ROOT), 0);
      used = Math.min(used, quota);
      LeaveBalanceEntity bal = new LeaveBalanceEntity();
      bal.setEmployeePk(emp.getId());
      bal.setEmployeeId(emp.getEmployeeId());
      bal.setLeaveTypeId(t.getId());
      bal.setLeaveType(t.getName());
      bal.setTotalLeave(quota);
      bal.setUsedLeave(used);
      bal.setRemainingLeave(Math.max(0, quota - used));
      applyAudit(bal);
      leaveBalances.save(bal);

      Map<String, Object> c = new LinkedHashMap<>();
      c.put("leaveTypeId", t.getId());
      c.put("leaveType", t.getName());
      c.put("total", quota);
      c.put("used", used);
      c.put("available", Math.max(0, quota - used));
      created.add(c);
    }
    return Map.of("employeeId", emp.getEmployeeId(), "balances", created);
  }

  /**
   * Ensure the logged-in doctor has an HR employee row and a balance card
   * for every leave type assigned to this tenant.
   */
  @Transactional
  public EmployeeEntity ensureSelfEmployeeWithBalances() {
    ensureLeaveCatalog();
    String doctorId = audit.doctorId();
    EmployeeEntity emp =
        employees
            .findByDoctorLinkId(doctorId)
            .orElseGet(
                () -> {
                  EmployeeEntity e = new EmployeeEntity();
                  e.setEmployeeId("EMP-" + doctorId.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
                  if (e.getEmployeeId().length() > 40) {
                    e.setEmployeeId(e.getEmployeeId().substring(0, 40));
                  }
                  e.setDoctorLinkId(doctorId);
                  e.setFirstName("Doctor");
                  e.setLastName(doctorId);
                  e.setEmployeeType("Doctor");
                  e.setEmploymentStatus("Active");
                  e.setJoiningDate(LocalDate.now().withDayOfYear(1));
                  applyAudit(e);
                  return employees.save(e);
                });

    List<LeaveTypeEntity> types = leaveTypes.findByDoctorIdOrderByNameAsc(doctorId);
    List<LeaveBalanceEntity> existing = leaveBalances.findByEmployeeId(emp.getEmployeeId());
    Set<String> have = new HashSet<>();
    for (LeaveBalanceEntity b : existing) {
      if (b.getLeaveTypeId() != null) {
        have.add(b.getLeaveTypeId());
      }
    }

    for (LeaveTypeEntity t : types) {
      if (have.contains(t.getId())) {
        continue;
      }
      int total = t.getAnnualQuota() == null ? 0 : t.getAnnualQuota();
      LeaveBalanceEntity bal = new LeaveBalanceEntity();
      bal.setEmployeePk(emp.getId());
      bal.setEmployeeId(emp.getEmployeeId());
      bal.setLeaveTypeId(t.getId());
      bal.setLeaveType(t.getName());
      bal.setTotalLeave(total);
      bal.setUsedLeave(0);
      bal.setRemainingLeave(total);
      applyAudit(bal);
      leaveBalances.save(bal);
    }
    return emp;
  }

  /** Dashboard payload: pending requests, balances, and chart series from approved leave. */
  @Transactional
  public Map<String, Object> leaveSummary(Integer year) {
    EmployeeEntity emp = ensureSelfEmployeeWithBalances();
    int y = year == null ? LocalDate.now().getYear() : year;
    String doctorId = audit.doctorId();
    Long hospitalId = audit.hospitalId();

    List<LeaveApplicationEntity> pending =
        leaveApplications
            .findByHospitalIdAndDoctorIdAndStatusOrderByCreationDateDesc(
                hospitalId, doctorId, "Pending")
            .stream()
            .filter(r -> emp.getEmployeeId().equals(r.getEmployeeId()))
            .toList();

    List<LeaveBalanceEntity> bals =
        leaveBalances.findByEmployeeId(emp.getEmployeeId()).stream()
            .filter(b -> doctorId.equals(b.getDoctorId()))
            .sorted(Comparator.comparing(b -> Optional.ofNullable(b.getLeaveType()).orElse("")))
            .toList();

    List<Map<String, Object>> balanceRows = new ArrayList<>();
    for (LeaveBalanceEntity b : bals) {
      int total = nz(b.getTotalLeave());
      int used = nz(b.getUsedLeave());
      int available = b.getRemainingLeave() != null ? b.getRemainingLeave() : Math.max(0, total - used);
      double pctAvailable = total <= 0 ? 0 : (available * 100.0) / total;
      double pctUsed = total <= 0 ? 0 : (used * 100.0) / total;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", b.getId());
      row.put("leaveTypeId", b.getLeaveTypeId());
      row.put("leaveType", b.getLeaveType());
      row.put("total", total);
      row.put("used", used);
      row.put("available", available);
      row.put("percentAvailable", Math.round(pctAvailable * 10) / 10.0);
      row.put("percentUsed", Math.round(pctUsed * 10) / 10.0);
      balanceRows.add(row);
    }

    List<LeaveApplicationEntity> approved =
        leaveApplications
            .findByHospitalIdAndDoctorIdAndStatusOrderByCreationDateDesc(
                hospitalId, doctorId, "Approved")
            .stream()
            .filter(r -> emp.getEmployeeId().equals(r.getEmployeeId()))
            .filter(
                r ->
                    r.getFromDate() != null
                        && (r.getFromDate().getYear() == y || r.getToDate().getYear() == y))
            .toList();

    int[] weekly = new int[7]; // Mon=0 … Sun=6
    int[] monthly = new int[12];
    Map<String, Integer> byType = new LinkedHashMap<>();

    for (LeaveApplicationEntity r : approved) {
      LocalDate cur = r.getFromDate();
      LocalDate end = r.getToDate();
      if (cur == null || end == null) continue;
      while (!cur.isAfter(end)) {
        if (cur.getYear() == y) {
          int dow = cur.getDayOfWeek().getValue() - 1; // Mon=0
          weekly[dow] += 1;
          monthly[cur.getMonthValue() - 1] += 1;
          String type = r.getLeaveType() == null ? "Other" : r.getLeaveType();
          byType.merge(type, 1, Integer::sum);
        }
        cur = cur.plusDays(1);
      }
    }

    List<Map<String, Object>> consumed = new ArrayList<>();
    String[] palette = {"#7c3aed", "#86efac", "#0ea5e9", "#f59e0b", "#f472b6", "#14b8a6"};
    int i = 0;
    for (Map.Entry<String, Integer> e : byType.entrySet()) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("leaveType", e.getKey());
      c.put("days", e.getValue());
      c.put("color", palette[i % palette.length]);
      consumed.add(c);
      i++;
    }

    Map<String, Object> empRow = new LinkedHashMap<>();
    empRow.put("id", emp.getId());
    empRow.put("employeeId", emp.getEmployeeId());
    empRow.put("name", emp.getFullName());
    empRow.put("doctorLinkId", emp.getDoctorLinkId());
    empRow.put("hospitalId", hospitalId);
    empRow.put("doctorId", doctorId);

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("weeklyPattern", Arrays.stream(weekly).boxed().toList());
    stats.put("weeklyLabels", List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
    stats.put("monthlyStats", Arrays.stream(monthly).boxed().toList());
    stats.put(
        "monthlyLabels",
        List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));
    stats.put("consumedByType", consumed);

    List<LeaveApplicationEntity> history =
        leaveApplications
            .findByHospitalIdAndDoctorIdOrderByCreationDateDesc(hospitalId, doctorId)
            .stream()
            .filter(r -> emp.getEmployeeId().equals(r.getEmployeeId()))
            .filter(
                r ->
                    r.getFromDate() == null
                        || r.getFromDate().getYear() == y
                        || (r.getToDate() != null && r.getToDate().getYear() == y)
                        || (r.getCreationDate() != null
                            && r.getCreationDate().atZone(java.time.ZoneId.systemDefault()).getYear()
                                == y))
            .toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("year", y);
    out.put("hospitalId", hospitalId);
    out.put("doctorId", doctorId);
    out.put("employee", empRow);
    out.put("pending", pending);
    out.put("history", history);
    out.put("balances", balanceRows);
    out.put("stats", stats);
    return out;
  }

  /**
   * Apply leave into svc.leave_applications (hospital_id, doctor_id, audit columns mandatory).
   * Body: leaveTypeId, fromDate, toDate, reason, autoApprove (default false — pending for Approver).
   */
  @Transactional
  public LeaveApplicationEntity requestLeaveForSelf(Map<String, Object> body) {
    EmployeeEntity emp = ensureSelfEmployeeWithBalances();
    String leaveTypeId = str(body.get("leaveTypeId"));
    if (leaveTypeId == null || leaveTypeId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leaveTypeId is required");
    }
    LeaveTypeEntity type =
        leaveTypes
            .findById(leaveTypeId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown leave type"));

    LocalDate from = LocalDate.parse(str(body.get("fromDate")));
    LocalDate to = LocalDate.parse(str(body.get("toDate")));
    if (to.isBefore(from)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toDate must be on/after fromDate");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;

    LeaveBalanceEntity bal =
        leaveBalances.findByEmployeeId(emp.getEmployeeId()).stream()
            .filter(b -> Objects.equals(b.getLeaveTypeId(), leaveTypeId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No balance for leave type " + type.getName()));

    int available =
        bal.getRemainingLeave() != null
            ? bal.getRemainingLeave()
            : Math.max(0, nz(bal.getTotalLeave()) - nz(bal.getUsedLeave()));
    if (days > available) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Only " + available + " day(s) available for " + type.getName());
    }

    boolean autoApprove =
        body.get("autoApprove") != null
            && Boolean.parseBoolean(String.valueOf(body.get("autoApprove")));

    LeaveApplicationEntity app = new LeaveApplicationEntity();
    app.setEmployeeId(emp.getEmployeeId());
    app.setEmployeeName(emp.getFullName());
    app.setLeaveTypeId(type.getId());
    app.setLeaveType(type.getName());
    app.setFromDate(from);
    app.setToDate(to);
    app.setDays(BigDecimal.valueOf(days));
    app.setReason(str(body.get("reason")));
    app.setStatus(autoApprove ? "Approved" : "Pending");
    app.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    leaveApplications.save(app);

    if (autoApprove) {
      bal.setUsedLeave(nz(bal.getUsedLeave()) + (int) days);
      bal.setRemainingLeave(Math.max(0, nz(bal.getTotalLeave()) - nz(bal.getUsedLeave())));
      applyAudit(bal);
      leaveBalances.save(bal);
    }
    return app;
  }

  @Transactional
  public LeaveApplicationEntity decideLeaveApplication(String id, String status, String remarks) {
    LeaveApplicationEntity app =
        or404(leaveApplications.findById(id), "Leave application not found");
    if (audit.hospitalId() != null && !audit.hospitalId().equals(app.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Leave is not in this hospital");
    }
    if (!"Pending".equalsIgnoreCase(app.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave is already " + app.getStatus());
    }
    if (!"Approved".equalsIgnoreCase(status) && !"Rejected".equalsIgnoreCase(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be Approved or Rejected");
    }
    app.setStatus("Approved".equalsIgnoreCase(status) ? "Approved" : "Rejected");
    app.setApproverRemarks(remarks);
    app.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    if ("Approved".equalsIgnoreCase(status)) {
      leaveBalances.findByEmployeeId(app.getEmployeeId()).stream()
          .filter(b -> Objects.equals(b.getLeaveTypeId(), app.getLeaveTypeId()))
          .findFirst()
          .ifPresent(
              b -> {
                int days =
                    app.getDays() != null
                        ? app.getDays().intValue()
                        : (int) (ChronoUnit.DAYS.between(app.getFromDate(), app.getToDate()) + 1);
                b.setUsedLeave(nz(b.getUsedLeave()) + days);
                b.setRemainingLeave(Math.max(0, nz(b.getTotalLeave()) - nz(b.getUsedLeave())));
                applyAudit(b);
                leaveBalances.save(b);
              });
    }
    return leaveApplications.save(app);
  }

  public List<LeaveApplicationEntity> listLeaveApplications(String status) {
    Long hospitalId = audit.hospitalId();
    String doctorId = audit.doctorId();
    if (status != null && !status.isBlank()) {
      return leaveApplications.findByHospitalIdAndDoctorIdAndStatusOrderByCreationDateDesc(
          hospitalId, doctorId, status);
    }
    return leaveApplications.findByHospitalIdAndDoctorIdOrderByCreationDateDesc(
        hospitalId, doctorId);
  }

  private static int nz(Integer v) {
    return v == null ? 0 : v;
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o).trim();
  }

  // —— Holidays ——
  public List<HolidayEntity> listHolidays() {
    return holidays.findByDoctorIdOrderByHolidayDateAsc(audit.doctorId());
  }

  @Transactional
  public HolidayEntity saveHoliday(HolidayEntity body) {
    applyAudit(body);
    return holidays.save(body);
  }

  @Transactional
  public void deleteHoliday(String id) {
    holidays.deleteById(id);
  }

  // —— Payroll ——
  public List<SalaryStructureEntity> listSalaries() {
    return salaries.findByDoctorIdOrderByEmployeeIdAsc(audit.doctorId());
  }

  @Transactional
  public SalaryStructureEntity saveSalary(SalaryStructureEntity body) {
    applyAudit(body);
    return salaries.save(body);
  }

  public List<PayslipEntity> listPayslips() {
    return payslips.findByDoctorIdOrderByPayYearDescPayMonthDesc(audit.doctorId());
  }

  @Transactional
  public PayslipEntity generatePayslip(Map<String, Object> req) {
    String employeeId = String.valueOf(req.get("employeeId"));
    int month = ((Number) req.get("payMonth")).intValue();
    int year = ((Number) req.get("payYear")).intValue();
    int workingDays =
        req.get("workingDays") == null ? 30 : ((Number) req.get("workingDays")).intValue();
    int presentDays =
        req.get("presentDays") == null ? workingDays : ((Number) req.get("presentDays")).intValue();
    int absentDays = Math.max(0, workingDays - presentDays);

    SalaryStructureEntity sal =
        or404(
            salaries.findByEmployeeIdAndDoctorId(employeeId, audit.doctorId()),
            "Salary structure not found for employee");

    BigDecimal basic = nz(sal.getBasicSalary());
    BigDecimal earnings =
        basic
            .add(nz(sal.getHra()))
            .add(nz(sal.getDa()))
            .add(nz(sal.getMedicalAllowance()))
            .add(nz(sal.getConveyance()))
            .add(nz(sal.getSpecialAllowance()));
    BigDecimal perDay =
        basic.divide(BigDecimal.valueOf(workingDays), 4, RoundingMode.HALF_UP);
    BigDecimal leaveDeduction = perDay.multiply(BigDecimal.valueOf(absentDays)).setScale(2, RoundingMode.HALF_UP);
    BigDecimal deductions =
        nz(sal.getPf())
            .add(nz(sal.getEsi()))
            .add(nz(sal.getProfessionalTax()))
            .add(nz(sal.getIncomeTax()))
            .add(leaveDeduction);
    BigDecimal net = earnings.subtract(deductions).setScale(2, RoundingMode.HALF_UP);

    PayslipEntity slip =
        payslips
            .findByEmployeeIdAndPayMonthAndPayYearAndDoctorId(employeeId, month, year, audit.doctorId())
            .orElseGet(PayslipEntity::new);
    slip.setEmployeePk(sal.getEmployeePk());
    slip.setEmployeeId(employeeId);
    slip.setEmployeeName(sal.getEmployeeName());
    slip.setPayMonth(month);
    slip.setPayYear(year);
    slip.setWorkingDays(workingDays);
    slip.setPresentDays(presentDays);
    slip.setAbsentDays(absentDays);
    slip.setGrossEarnings(earnings.setScale(2, RoundingMode.HALF_UP));
    slip.setLeaveDeduction(leaveDeduction);
    slip.setTotalDeductions(deductions.setScale(2, RoundingMode.HALF_UP));
    slip.setNetSalary(net);
    slip.setEarningsJson(
        "{\"basic\":"
            + basic
            + ",\"hra\":"
            + nz(sal.getHra())
            + ",\"da\":"
            + nz(sal.getDa())
            + ",\"medical\":"
            + nz(sal.getMedicalAllowance())
            + ",\"conveyance\":"
            + nz(sal.getConveyance())
            + ",\"special\":"
            + nz(sal.getSpecialAllowance())
            + "}");
    slip.setDeductionsJson(
        "{\"pf\":"
            + nz(sal.getPf())
            + ",\"esi\":"
            + nz(sal.getEsi())
            + ",\"pt\":"
            + nz(sal.getProfessionalTax())
            + ",\"it\":"
            + nz(sal.getIncomeTax())
            + ",\"leave\":"
            + leaveDeduction
            + "}");
    applyAudit(slip);
    return payslips.save(slip);
  }

  private BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  // —— Performance ——
  public List<PerformanceReviewEntity> listReviews() {
    return reviews.findByDoctorIdOrderByReviewDateDesc(audit.doctorId());
  }

  @Transactional
  public PerformanceReviewEntity saveReview(PerformanceReviewEntity body) {
    applyAudit(body);
    return reviews.save(body);
  }

  // —— Recruitment ——
  public List<CandidateEntity> listCandidates() {
    return candidates.findByDoctorIdOrderByCreationDateDesc(audit.doctorId());
  }

  @Transactional
  public CandidateEntity saveCandidate(CandidateEntity body) {
    applyAudit(body);
    return candidates.save(body);
  }

  public List<InterviewEntity> listInterviews() {
    return interviews.findByDoctorIdOrderByInterviewDateDesc(audit.doctorId());
  }

  @Transactional
  public InterviewEntity saveInterview(InterviewEntity body) {
    applyAudit(body);
    return interviews.save(body);
  }

  // —— Onboarding ——
  public List<OnboardingEntity> listOnboarding() {
    return onboardings.findByDoctorIdOrderByCreationDateDesc(audit.doctorId());
  }

  @Transactional
  public OnboardingEntity saveOnboarding(OnboardingEntity body) {
    boolean done =
        Boolean.TRUE.equals(body.getDocumentVerification())
            && Boolean.TRUE.equals(body.getIdCardGenerated())
            && Boolean.TRUE.equals(body.getEmailCreated())
            && Boolean.TRUE.equals(body.getDepartmentAssigned())
            && Boolean.TRUE.equals(body.getShiftAssigned())
            && Boolean.TRUE.equals(body.getSalaryAssigned());
    body.setStatus(done ? "Completed" : "In Progress");
    if (body.getId() != null) {
      OnboardingEntity existing = or404(onboardings.findById(body.getId()), "Onboarding not found");
      existing.setDocumentVerification(body.getDocumentVerification());
      existing.setIdCardGenerated(body.getIdCardGenerated());
      existing.setEmailCreated(body.getEmailCreated());
      existing.setDepartmentAssigned(body.getDepartmentAssigned());
      existing.setShiftAssigned(body.getShiftAssigned());
      existing.setSalaryAssigned(body.getSalaryAssigned());
      existing.setStatus(body.getStatus());
      applyAudit(existing);
      return onboardings.save(existing);
    }
    applyAudit(body);
    return onboardings.save(body);
  }

  // —— Assets ——
  public List<AssetEntity> listAssets() {
    return assets.findByDoctorIdOrderByAllocatedDateDesc(audit.doctorId());
  }

  @Transactional
  public AssetEntity saveAsset(AssetEntity body) {
    if (body.getAllocatedDate() == null) body.setAllocatedDate(LocalDate.now());
    applyAudit(body);
    return assets.save(body);
  }

  // —— Training ——
  public List<TrainingEntity> listTrainings() {
    return trainings.findByDoctorIdOrderByStartDateDesc(audit.doctorId());
  }

  @Transactional
  public TrainingEntity saveTraining(TrainingEntity body) {
    applyAudit(body);
    return trainings.save(body);
  }

  // —— Exit ——
  public List<ResignationEntity> listResignations() {
    return resignations.findByDoctorIdOrderByResignationDateDesc(audit.doctorId());
  }

  @Transactional
  public ResignationEntity saveResignation(ResignationEntity body) {
    applyAudit(body);
    if (body.getId() != null) {
      ResignationEntity existing = or404(resignations.findById(body.getId()), "Resignation not found");
      existing.setResignationDate(body.getResignationDate());
      existing.setLastWorkingDay(body.getLastWorkingDay());
      existing.setReason(body.getReason());
      existing.setAssetReturn(body.getAssetReturn());
      existing.setClearance(body.getClearance());
      existing.setFinalSettlement(body.getFinalSettlement());
      existing.setExperienceLetter(body.getExperienceLetter());
      existing.setRelievingLetter(body.getRelievingLetter());
      boolean done =
          Boolean.TRUE.equals(existing.getAssetReturn())
              && Boolean.TRUE.equals(existing.getClearance())
              && Boolean.TRUE.equals(existing.getFinalSettlement())
              && Boolean.TRUE.equals(existing.getExperienceLetter())
              && Boolean.TRUE.equals(existing.getRelievingLetter());
      existing.setStatus(done ? "Completed" : body.getStatus() == null ? existing.getStatus() : body.getStatus());
      if (done) {
        employees
            .findByEmployeeId(existing.getEmployeeId())
            .ifPresent(
                e -> {
                  e.setEmploymentStatus("Inactive");
                  applyAudit(e);
                  employees.save(e);
                });
      }
      applyAudit(existing);
      return resignations.save(existing);
    }
    if (body.getStatus() == null) body.setStatus("Submitted");
    return resignations.save(body);
  }

  // —— Seed helpers used by seeder ——
  public void ensureSeed() {
    String d = audit.doctorId();
    if (!departments.findByDoctorIdOrderByNameAsc(d).isEmpty()) return;

    String[] deptNames = {
      "Cardiology", "Orthopedics", "Neurology", "Emergency", "Radiology",
      "Laboratory", "Pharmacy", "HR", "Accounts"
    };
    List<DepartmentEntity> deps = new ArrayList<>();
    for (String n : deptNames) {
      DepartmentEntity e = new DepartmentEntity();
      e.setName(n);
      e.setDescription(n + " department");
      e.setDepartmentHead("Head - " + n);
      applyAudit(e);
      deps.add(departments.save(e));
    }

    String[][] desigs = {
      {"Senior Doctor", "A1"}, {"Junior Doctor", "A2"}, {"Nurse", "B1"},
      {"Staff Nurse", "B2"}, {"Receptionist", "C1"}, {"HR Executive", "C2"}
    };
    for (String[] row : desigs) {
      DesignationEntity e = new DesignationEntity();
      e.setName(row[0]);
      e.setSalaryGrade(row[1]);
      e.setDepartmentId(deps.get(0).getId());
      e.setDepartmentName(deps.get(0).getName());
      applyAudit(e);
      designations.save(e);
    }

    Object[][] shiftRows = {
      {"Morning", LocalTime.of(7, 0), LocalTime.of(15, 0), 30},
      {"Evening", LocalTime.of(15, 0), LocalTime.of(23, 0), 30},
      {"Night", LocalTime.of(23, 0), LocalTime.of(7, 0), 30},
      {"General", LocalTime.of(9, 0), LocalTime.of(18, 0), 60}
    };
    for (Object[] row : shiftRows) {
      ShiftEntity e = new ShiftEntity();
      e.setName((String) row[0]);
      e.setStartTime((LocalTime) row[1]);
      e.setEndTime((LocalTime) row[2]);
      e.setBreakDurationMinutes((Integer) row[3]);
      applyAudit(e);
      shifts.save(e);
    }

    String[][] leaveRows = {
      {"Casual Leave", "12"}, {"Sick Leave", "10"}, {"Earned Leave", "15"},
      {"Maternity Leave", "180"}, {"Paternity Leave", "15"}, {"Emergency Leave", "5"}
    };
    for (String[] row : leaveRows) {
      LeaveTypeEntity e = new LeaveTypeEntity();
      e.setName(row[0]);
      e.setAnnualQuota(Integer.parseInt(row[1]));
      applyAudit(e);
      leaveTypes.save(e);
    }

    Object[][] hols = {
      {"Republic Day", LocalDate.of(LocalDate.now().getYear(), 1, 26)},
      {"Holi", LocalDate.of(LocalDate.now().getYear(), 3, 14)},
      {"Independence Day", LocalDate.of(LocalDate.now().getYear(), 8, 15)},
      {"Mahatma Gandhi Jayanti", LocalDate.of(LocalDate.now().getYear(), 10, 2)},
      {"Diwali", LocalDate.of(LocalDate.now().getYear(), 10, 20)}
    };
    for (Object[] row : hols) {
      HolidayEntity e = new HolidayEntity();
      e.setName((String) row[0]);
      e.setHolidayDate((LocalDate) row[1]);
      e.setDescription((String) row[0]);
      applyAudit(e);
      holidays.save(e);
    }
  }
}
