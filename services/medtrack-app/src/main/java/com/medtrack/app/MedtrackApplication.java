package com.medtrack.app;

import com.medtrack.app.status.ServiceStatusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.medtrack")
@EntityScan(basePackages = "com.medtrack")
@EnableJpaRepositories(basePackages = "com.medtrack")
@EnableScheduling
@EnableConfigurationProperties(ServiceStatusProperties.class)
public class MedtrackApplication {
  public static void main(String[] args) {
    SpringApplication.run(MedtrackApplication.class, args);
  }
}
