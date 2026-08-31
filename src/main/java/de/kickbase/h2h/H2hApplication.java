package de.kickbase.h2h;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(KickbaseProperties.class)
public class H2hApplication {
  public static void main(String[] args) { SpringApplication.run(H2hApplication.class, args); }
}
