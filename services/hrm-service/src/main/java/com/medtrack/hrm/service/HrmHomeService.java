package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.*;
import com.medtrack.hrm.repo.*;
import com.medtrack.hrm.web.AuditContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrmHomeService {
  private final AuditContext audit;
  private final HrmAppService hrm;
  private final HolidayRepository holidays;
  private final LeaveApplicationRepository leaveApps;
  private final LeaveRequestRepository leaveReqs;
  private final WfhRequestRepository wfh;
  private final EmployeeRepository employees;
  private final HrmInboxRepository inbox;
  private final HrmDepartmentRepository departments;
  private final HrmPostRepository posts;
  private final HrmPostLikeRepository postLikes;
  private final HrmPostCommentRepository postComments;

  public HrmHomeService(
      AuditContext audit,
      @Lazy HrmAppService hrm,
      HolidayRepository holidays,
      LeaveApplicationRepository leaveApps,
      LeaveRequestRepository leaveReqs,
      WfhRequestRepository wfh,
      EmployeeRepository employees,
      HrmInboxRepository inbox,
      HrmDepartmentRepository departments,
      HrmPostRepository posts,
      HrmPostLikeRepository postLikes,
      HrmPostCommentRepository postComments) {
    this.audit = audit;
    this.hrm = hrm;
    this.holidays = holidays;
    this.leaveApps = leaveApps;
    this.leaveReqs = leaveReqs;
    this.wfh = wfh;
    this.employees = employees;
    this.inbox = inbox;
    this.departments = departments;
    this.posts = posts;
    this.postLikes = postLikes;
    this.postComments = postComments;
  }

  @Transactional
  public Map<String, Object> home() {
    LocalDate today = LocalDate.now();
    String doctorId = audit.doctorId();
    Long hospitalId = audit.hospitalId();

    Map<String, Object> attendance = hrm.attendanceBoard();
    Map<String, Object> leave = hrm.leaveSummary(today.getYear());

    long pending =
        inbox.countByHospitalIdAndDoctorIdAndStatusAndArchived(
            hospitalId, doctorId, "PENDING", false);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("today", today.toString());
    out.put("inboxPendingCount", pending);
    out.put("holidays", upcomingHolidays(hospitalId, doctorId, today));
    out.put("onLeaveToday", onLeaveToday(hospitalId, doctorId, today));
    out.put("workingRemotely", workingRemotely(hospitalId, doctorId, today));
    out.put("attendance", attendance);
    out.put("leaveBalances", leave.get("balances"));
    out.put("departments", departmentTabs(doctorId));
    out.put("announcements", announcements(hospitalId, doctorId));
    out.put("birthdaysToday", birthdays(doctorId, today, true));
    out.put("upcomingBirthdays", birthdays(doctorId, today, false));
    out.put("workAnniversaryCount", anniversaryCount(doctorId, today));
    out.put("newJoineeCount", newJoineeCount(doctorId, today));
    return out;
  }

  private List<Map<String, Object>> upcomingHolidays(
      Long hospitalId, String doctorId, LocalDate today) {
    List<HolidayEntity> hospitalRows =
        hospitalId == null
            ? List.of()
            : holidays.findByHospitalIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(
                hospitalId, today);
    List<HolidayEntity> doctorRows =
        holidays.findByDoctorIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(doctorId, today);
    LinkedHashMap<String, HolidayEntity> byDate = new LinkedHashMap<>();
    for (HolidayEntity h : doctorRows) {
      if (h.getHolidayDate() != null) byDate.put(h.getHolidayDate().toString(), h);
    }
    for (HolidayEntity h : hospitalRows) {
      if (h.getHolidayDate() != null) byDate.put(h.getHolidayDate().toString(), h);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (HolidayEntity h : byDate.values()) {
      if (out.size() >= 20) break;
      out.add(HrmHolidayService.toMap(h));
    }
    return out;
  }

  private List<Map<String, Object>> onLeaveToday(Long hospitalId, String doctorId, LocalDate today) {
    List<Map<String, Object>> people = new ArrayList<>();
    for (LeaveApplicationEntity r :
        leaveApps.findByHospitalIdAndDoctorIdAndStatusOrderByCreationDateDesc(
            hospitalId, doctorId, "Approved")) {
      if (covers(r.getFromDate(), r.getToDate(), today)) {
        people.add(person(r.getEmployeeName()));
      }
    }
    for (LeaveRequestEntity r : leaveReqs.findByDoctorIdAndStatus(doctorId, "Approved")) {
      if (covers(r.getFromDate(), r.getToDate(), today)) {
        people.add(person(r.getEmployeeName()));
      }
    }
    return uniquePeople(people);
  }

  private List<Map<String, Object>> workingRemotely(
      Long hospitalId, String doctorId, LocalDate today) {
    List<Map<String, Object>> people = new ArrayList<>();
    for (WfhRequestEntity r :
        wfh.findByHospitalIdAndDoctorIdOrderByCreationDateDesc(hospitalId, doctorId)) {
      String status = r.getStatus() == null ? "" : r.getStatus();
      if ("Rejected".equalsIgnoreCase(status) || "Cancelled".equalsIgnoreCase(status)) continue;
      if (covers(r.getFromDate(), r.getToDate(), today)) {
        people.add(person(r.getEmployeeName()));
      }
    }
    return uniquePeople(people);
  }

  private List<Map<String, Object>> departmentTabs(String doctorId) {
    List<Map<String, Object>> tabs = new ArrayList<>();
    tabs.add(Map.of("id", "org", "name", "Organization"));
    for (DepartmentEntity d : departments.findByDoctorIdOrderByNameAsc(doctorId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", d.getId());
      row.put("name", d.getName());
      tabs.add(row);
    }
    if (tabs.size() == 1) {
      tabs.add(Map.of("id", "eng", "name", "Engineering > OpEx, R&D"));
    }
    return tabs;
  }

  @Transactional
  public Map<String, Object> createPost(Map<String, Object> body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post text is required");
    }
    String text = body.get("body") == null ? "" : String.valueOf(body.get("body")).trim();
    if (text.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post text is required");
    }
    String title =
        body.get("title") == null ? "" : String.valueOf(body.get("title")).trim();
    if (title.isBlank()) {
      String first = text.split("\\R", 2)[0].trim();
      title = first.length() > 80 ? first.substring(0, 77) + "…" : first;
    }
    if (title.length() > 240) title = title.substring(0, 237) + "…";

    HrmPostEntity row = new HrmPostEntity();
    row.setTitle(title);
    row.setBody(text);
    row.setLikes(0);
    row.setComments(0);
    row.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    HrmPostEntity saved = posts.save(row);
    return toAnnouncement(saved);
  }

  @Transactional
  public Map<String, Object> toggleLike(String postId) {
    HrmPostEntity post = requireHospitalPost(postId);
    String userId = currentUserId();
    var existing = postLikes.findByPostIdAndUserId(post.getId(), userId);
    if (existing.isPresent()) {
      postLikes.deleteByPostIdAndUserId(post.getId(), userId);
    } else {
      HrmPostLikeEntity like = new HrmPostLikeEntity();
      like.setPostId(post.getId());
      like.setUserId(clip(userId, 64));
      like.setUserName(clip(displayName(), 160));
      like.touchAudit(audit.hospitalId(), audit.user());
      postLikes.save(like);
    }
    syncEngagementCounts(post);
    return toAnnouncement(post);
  }

  @Transactional
  public Map<String, Object> addComment(String postId, Map<String, Object> body) {
    HrmPostEntity post = requireHospitalPost(postId);
    String text = body == null || body.get("body") == null ? "" : String.valueOf(body.get("body")).trim();
    if (text.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment text is required");
    }
    if (text.length() > 2000) text = text.substring(0, 2000);
    HrmPostCommentEntity row = new HrmPostCommentEntity();
    row.setPostId(post.getId());
    row.setUserId(clip(currentUserId(), 64));
    row.setUserName(clip(displayName(), 160));
    row.setBody(text);
    row.touchAudit(audit.hospitalId(), audit.user());
    postComments.save(row);
    syncEngagementCounts(post);
    return toAnnouncement(post);
  }

  public List<Map<String, Object>> listComments(String postId) {
    HrmPostEntity post = requireHospitalPost(postId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (HrmPostCommentEntity c : postComments.findByPostIdOrderByCreationDateAsc(post.getId())) {
      out.add(toComment(c));
    }
    return out;
  }

  private HrmPostEntity requireHospitalPost(String postId) {
    if (postId == null || postId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post id is required");
    }
    HrmPostEntity post =
        posts
            .findById(postId.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    if (audit.hospitalId() != null && !audit.hospitalId().equals(post.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Post is not in this hospital");
    }
    return post;
  }

  private void syncEngagementCounts(HrmPostEntity post) {
    int likes = (int) postLikes.countByPostId(post.getId());
    int comments = (int) postComments.countByPostId(post.getId());
    post.setLikes(likes);
    post.setComments(comments);
    post.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    posts.save(post);
  }

  private List<Map<String, Object>> announcements(Long hospitalId, String doctorId) {
    List<HrmPostEntity> rows = posts.findByHospitalIdOrderByCreationDateDesc(hospitalId);
    if (rows.size() > 20) rows = rows.subList(0, 20);
    List<String> ids = rows.stream().map(HrmPostEntity::getId).toList();
    Map<String, List<HrmPostCommentEntity>> commentsByPost = new HashMap<>();
    Map<String, Integer> likeCounts = new HashMap<>();
    Set<String> likedByMe = new HashSet<>();
    String me = currentUserId();
    if (!ids.isEmpty()) {
      for (HrmPostLikeEntity like : postLikes.findByPostIdIn(ids)) {
        likeCounts.merge(like.getPostId(), 1, Integer::sum);
        if (me.equalsIgnoreCase(like.getUserId())) likedByMe.add(like.getPostId());
      }
      for (HrmPostCommentEntity c : postComments.findByPostIdInOrderByCreationDateAsc(ids)) {
        commentsByPost.computeIfAbsent(c.getPostId(), k -> new ArrayList<>()).add(c);
      }
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (HrmPostEntity p : rows) {
      items.add(
          toAnnouncement(
              p,
              likeCounts.getOrDefault(p.getId(), 0),
              commentsByPost.getOrDefault(p.getId(), List.of()),
              likedByMe.contains(p.getId())));
    }
    return items;
  }

  private Map<String, Object> toAnnouncement(HrmPostEntity p) {
    List<HrmPostCommentEntity> comments = postComments.findByPostIdOrderByCreationDateAsc(p.getId());
    int likes = (int) postLikes.countByPostId(p.getId());
    boolean liked = postLikes.existsByPostIdAndUserId(p.getId(), currentUserId());
    return toAnnouncement(p, likes, comments, liked);
  }

  private Map<String, Object> toAnnouncement(
      HrmPostEntity p,
      int likes,
      List<HrmPostCommentEntity> comments,
      boolean likedByMe) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.getId());
    row.put("title", p.getTitle());
    row.put("body", p.getBody());
    row.put("likes", likes);
    row.put("comments", comments.size());
    row.put("likedByMe", likedByMe);
    row.put("author", p.getCreationUser());
    row.put("createdAt", p.getCreationDate() == null ? null : p.getCreationDate().toString());
    List<Map<String, Object>> commentItems = new ArrayList<>();
    for (HrmPostCommentEntity c : comments) {
      commentItems.add(toComment(c));
    }
    row.put("commentItems", commentItems);
    return row;
  }

  private static Map<String, Object> toComment(HrmPostCommentEntity c) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", c.getId());
    row.put("postId", c.getPostId());
    row.put("userId", c.getUserId());
    row.put("userName", c.getUserName() == null || c.getUserName().isBlank() ? c.getCreationUser() : c.getUserName());
    row.put("body", c.getBody());
    row.put("createdAt", c.getCreationDate() == null ? null : c.getCreationDate().toString());
    return row;
  }

  private String currentUserId() {
    String user = audit.user();
    if (user != null && !user.isBlank() && !"system".equalsIgnoreCase(user)) return user.trim();
    return audit.doctorId() == null ? "system" : audit.doctorId();
  }

  private String displayName() {
    String user = audit.user();
    if (user != null && !user.isBlank() && !"system".equalsIgnoreCase(user)) return user.trim();
    return audit.doctorId() == null ? "Team member" : audit.doctorId();
  }

  private static String clip(String value, int max) {
    if (value == null) return "";
    String v = value.trim();
    return v.length() <= max ? v : v.substring(0, max);
  }

  private List<Map<String, Object>> birthdays(String doctorId, LocalDate today, boolean todayOnly) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (EmployeeEntity e : employees.findByDoctorIdOrderByEmployeeIdAsc(doctorId)) {
      if (e.getDateOfBirth() == null) continue;
      LocalDate next = e.getDateOfBirth().withYear(today.getYear());
      if (next.isBefore(today)) next = next.plusYears(1);
      long days = ChronoUnit.DAYS.between(today, next);
      boolean isToday = days == 0;
      if (todayOnly && !isToday) continue;
      if (!todayOnly && (isToday || days > 30)) continue;
      Map<String, Object> row = person(e.getFullName());
      row.put("dateOfBirth", e.getDateOfBirth().toString());
      row.put("daysUntil", days);
      out.add(row);
      if (out.size() >= 8) break;
    }
    return out;
  }

  private long anniversaryCount(String doctorId, LocalDate today) {
    return employees.findByDoctorIdOrderByEmployeeIdAsc(doctorId).stream()
        .filter(e -> e.getJoiningDate() != null)
        .filter(e -> e.getJoiningDate().getYear() < today.getYear())
        .filter(
            e ->
                e.getJoiningDate().getMonth() == today.getMonth()
                    && e.getJoiningDate().getDayOfMonth() == today.getDayOfMonth())
        .count();
  }

  private long newJoineeCount(String doctorId, LocalDate today) {
    return employees.findByDoctorIdOrderByEmployeeIdAsc(doctorId).stream()
        .filter(e -> e.getJoiningDate() != null)
        .filter(e -> !e.getJoiningDate().isBefore(today.minusDays(30)))
        .filter(e -> !e.getJoiningDate().isAfter(today))
        .count();
  }

  private static boolean covers(LocalDate from, LocalDate to, LocalDate day) {
    return from != null && to != null && !from.isAfter(day) && !to.isBefore(day);
  }

  private static Map<String, Object> person(String name) {
    String n = name == null || name.isBlank() ? "Team member" : name.trim();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", n);
    m.put("initials", initials(n));
    return m;
  }

  private static List<Map<String, Object>> uniquePeople(List<Map<String, Object>> people) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> p : people) {
      String name = String.valueOf(p.get("name")).toLowerCase(Locale.ROOT);
      boolean seen = out.stream().anyMatch(x -> String.valueOf(x.get("name")).equalsIgnoreCase(name));
      if (!seen) out.add(p);
    }
    return out;
  }

  private static String initials(String name) {
    String[] parts = name.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
      if (sb.length() >= 2) break;
    }
    return sb.length() == 0 ? "TM" : sb.toString();
  }
}
