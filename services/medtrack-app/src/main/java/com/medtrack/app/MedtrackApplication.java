package com.medtrack.app;

import com.medtrack.app.status.ServiceStatusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.medtrack")
@EntityScan(basePackages = "com.medtrack")
@EnableJpaRepositories(basePackages = "com.medtrack")
@EnableConfigurationProperties(ServiceStatusProperties.class)
public class MedtrackApplication {
  public static void main(String[] args) {
    SpringApplication.run(MedtrackApplication.class, args);
  }
}
