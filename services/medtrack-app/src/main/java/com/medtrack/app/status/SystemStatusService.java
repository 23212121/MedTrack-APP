package com.medtrack.app.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.SystemHealth;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {
  private final HealthEndpoint healthEndpoint;
  private final ServiceStatusProperties props;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

  public SystemStatusService(HealthEndpoint healthEndpoint, ServiceStatusProperties props) {
    this.healthEndpoint = healthEndpoint;
    this.props = props;
  }

  public Map<String, Object> aggregate() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("checkedAt", Instant.now().toString());
    out.put("mode", "medtrack-app (unified jar on :8090)");

    HealthComponent health = healthEndpoint.health();
    Map<String, Object> local = new LinkedHashMap<>();
    local.put("name", "medtrack-app");
    local.put("port", 8090);
    local.put("status", statusOf(health));
    local.put("actuator", "/actuator/health");
    if (health instanceof SystemHealth systemHealth) {
      Map<String, Object> components = new LinkedHashMap<>();
      systemHealth
          .getComponents()
          .forEach((name, component) -> components.put(name, statusOf(component)));
      local.put("components", components);
    }
    out.put("local", local);

    List<Map<String, Object>> services = new ArrayList<>();
    int up = 0;
    int down = 0;
    int requiredDown = 0;
    for (ServiceStatusProperties.RemoteService svc : props.getServices()) {
      Map<String, Object> row = probe(svc);
      services.add(row);
      String st = String.valueOf(row.get("status"));
      if ("UP".equals(st) || "EMBEDDED".equals(st)) {
        up++;
      } else {
        down++;
        // Only required/embedded modules affect overall when missing
        if (svc.isEmbedded() || svc.getPort() == 8090) {
          requiredDown++;
        }
      }
    }
    out.put("services", services);
    out.put("summary", Map.of("up", up, "down", down, "total", services.size()));
    String localStatus = String.valueOf(local.get("status"));
    String overall;
    if (!"UP".equals(localStatus)) {
      overall = "DOWN";
    } else if (requiredDown > 0) {
      overall = "DEGRADED";
    } else {
      overall = "UP";
    }
    out.put("overall", overall);
    return out;
  }

  private Map<String, Object> probe(ServiceStatusProperties.RemoteService svc) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", svc.getName());
    row.put("port", svc.getPort());
    row.put("url", svc.getUrl());
    row.put("embedded", svc.isEmbedded());

    // Same host/port as this process — treat as local actuator (always reachable when we run)
    if (svc.getPort() == 8090 || (svc.getUrl() != null && svc.getUrl().contains(":8090/"))) {
      HealthComponent health = healthEndpoint.health();
      row.put("status", statusOf(health));
      row.put("mode", "local-actuator");
      row.put("detail", "Served by medtrack-app on :8090");
      return row;
    }

    if (svc.isEmbedded()) {
      // Packaged inside medtrack-app — report EMBEDDED/UP without requiring a separate process
      row.put("status", "EMBEDDED");
      row.put("mode", "embedded-in-medtrack-app");
      row.put("detail", "Runs inside medtrack-app (not a separate process)");
      // Still try remote probe in case user started standalone instance
      Map<String, Object> remote = httpProbe(svc.getUrl());
      if ("UP".equals(remote.get("status"))) {
        row.put("status", "UP");
        row.put("mode", "standalone");
        row.put("detail", "Separate process responded on port " + svc.getPort());
        row.put("httpStatus", remote.get("httpStatus"));
      }
      return row;
    }

    Map<String, Object> remote = httpProbe(svc.getUrl());
    row.putAll(remote);
    row.put("mode", "standalone");
    return row;
  }

  private Map<String, Object> httpProbe(String url) {
    Map<String, Object> remote = new LinkedHashMap<>();
    if (url == null || url.isBlank()) {
      remote.put("status", "UNKNOWN");
      remote.put("detail", "No health URL configured");
      return remote;
    }
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofMillis(Math.max(500, props.getProbeTimeoutMs())))
              .GET()
              .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      remote.put("httpStatus", res.statusCode());
      if (res.statusCode() >= 200 && res.statusCode() < 300) {
        String body = res.body() == null ? "" : res.body();
        if (body.contains("\"status\":\"UP\"") || body.contains("\"status\": \"UP\"")) {
          remote.put("status", "UP");
        } else if (body.contains("\"status\":\"DOWN\"") || body.contains("\"status\": \"DOWN\"")) {
          remote.put("status", "DOWN");
        } else {
          remote.put("status", "UP");
        }
        remote.put("detail", "Actuator health OK");
      } else {
        remote.put("status", "DOWN");
        remote.put("detail", "HTTP " + res.statusCode());
      }
    } catch (Exception ex) {
      remote.put("status", "DOWN");
      remote.put("detail", ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }
    return remote;
  }

  private static String statusOf(HealthComponent component) {
    if (component == null || component.getStatus() == null) {
      return "UNKNOWN";
    }
    return component.getStatus().getCode();
  }
}
