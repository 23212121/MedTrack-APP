package com.medtrack.hrm.web;

import com.medtrack.hrm.domain.WfhRequestEntity;
import com.medtrack.hrm.service.HrmWfhService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hrm/wfh")
public class HrmWfhController {
  private final HrmWfhService wfh;

  public HrmWfhController(HrmWfhService wfh) {
    this.wfh = wfh;
  }

  @GetMapping
  public List<WfhRequestEntity> list() {
    return wfh.list();
  }

  @PostMapping
  public WfhRequestEntity request(@RequestBody Map<String, Object> body) {
    return wfh.request(body);
  }
}
