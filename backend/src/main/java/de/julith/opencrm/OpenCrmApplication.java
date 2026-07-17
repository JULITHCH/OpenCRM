package de.julith.opencrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "OpenCRM", sharedModules = "shared")
@SpringBootApplication
public class OpenCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenCrmApplication.class, args);
    }
}
