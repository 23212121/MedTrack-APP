package com.medtrack.booking.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lets Jackson read and write {@code java.time} fields such as {@code LocalDate}. */
@Configuration
public class JacksonJavaTimeConfig {

  @Bean
  public Module javaTimeModule() {
    return new JavaTimeModule();
  }
}
