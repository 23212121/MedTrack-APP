package com.medtrack.hrm.web;

import com.medtrack.hrm.service.HrmRightsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrm/rights")
public class HrmRightsController {
  private final HrmRightsService rights;

  public HrmRightsController(HrmRightsService rights) {
    this.rights = rights;
  }

  @GetMapping
  public Map<String, Object> current() {
    return rights.currentRights();
  }

  @PutMapping
  public Map<String, Object> grant(@RequestBody Map<String, Object> body) {
    return rights.grant(body);
  }
}
