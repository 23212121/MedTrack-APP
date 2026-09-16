package com.medtrack.hrm.web;

import com.medtrack.hrm.service.HrmAppService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrm")
public class HrmEmployeeController {
  private final HrmAppService hrm;

  public HrmEmployeeController(HrmAppService hrm) {
    this.hrm = hrm;
  }

  @GetMapping("/employees")
  public Map<String, Object> employees() {
    List<Map<String, Object>> list = hrm.listRosterEmployees();
    return Map.of("count", list.size(), "employees", list);
  }
}
