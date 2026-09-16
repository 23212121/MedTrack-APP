package com.medtrack.hrm.web;

import com.medtrack.hrm.service.HrmHolidayService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrm/holidays")
public class HrmHolidayController {
  private final HrmHolidayService holidays;

  public HrmHolidayController(HrmHolidayService holidays) {
    this.holidays = holidays;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return holidays.list();
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    return holidays.create(body);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    holidays.delete(id);
  }
}
