package com.medtrack.hrm.web;

import com.medtrack.hrm.domain.AttendanceEntity;
import com.medtrack.hrm.service.HrmAppService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrm/attendance")
public class HrmAttendanceController {
  private final HrmAppService hrm;

  public HrmAttendanceController(HrmAppService hrm) {
    this.hrm = hrm;
  }

  @GetMapping
  public Map<String, Object> board() {
    return hrm.attendanceBoard();
  }

  @PostMapping("/clock-in")
  public AttendanceEntity clockIn() {
    return hrm.clockIn();
  }

  @PostMapping("/clock-out")
  public AttendanceEntity clockOut() {
    return hrm.clockOut();
  }
}
