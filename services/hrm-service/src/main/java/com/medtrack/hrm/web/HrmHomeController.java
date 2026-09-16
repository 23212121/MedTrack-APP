package com.medtrack.hrm.web;

import com.medtrack.hrm.service.HrmHomeService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrm")
public class HrmHomeController {
  private final HrmHomeService home;

  public HrmHomeController(HrmHomeService home) {
    this.home = home;
  }

  @GetMapping("/home")
  public Map<String, Object> home() {
    return home.home();
  }

  @PostMapping("/posts")
  public Map<String, Object> createPost(@RequestBody Map<String, Object> body) {
    return home.createPost(body);
  }

  @PostMapping("/posts/{id}/like")
  public Map<String, Object> toggleLike(@PathVariable String id) {
    return home.toggleLike(id);
  }

  @GetMapping("/posts/{id}/comments")
  public List<Map<String, Object>> comments(@PathVariable String id) {
    return home.listComments(id);
  }

  @PostMapping("/posts/{id}/comments")
  public Map<String, Object> addComment(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    return home.addComment(id, body);
  }
}
