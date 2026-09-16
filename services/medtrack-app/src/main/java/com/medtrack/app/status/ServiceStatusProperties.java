package com.medtrack.app.status;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medtrack.status")
public class ServiceStatusProperties {
  private int probeTimeoutMs = 2000;
  private List<RemoteService> services = new ArrayList<>();

  public int getProbeTimeoutMs() {
    return probeTimeoutMs;
  }

  public void setProbeTimeoutMs(int probeTimeoutMs) {
    this.probeTimeoutMs = probeTimeoutMs;
  }

  public List<RemoteService> getServices() {
    return services;
  }

  public void setServices(List<RemoteService> services) {
    this.services = services != null ? services : new ArrayList<>();
  }

  public static class RemoteService {
    private String name;
    private int port;
    private String url;
    /** true = packaged inside medtrack-app (expected UP when monolith runs). */
    private boolean embedded;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public boolean isEmbedded() {
      return embedded;
    }

    public void setEmbedded(boolean embedded) {
      this.embedded = embedded;
    }
  }
}
