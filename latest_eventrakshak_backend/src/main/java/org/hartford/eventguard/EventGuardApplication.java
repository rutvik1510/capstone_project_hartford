package org.hartford.eventguard;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventGuardApplication {

    public static void main(String[] args) {
        // Load .env variables into System properties BEFORE starting Spring
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./") // Looks for .env in current folder (latest_eventrakshak_backend)
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            
            dotenv.entries().forEach(entry -> {
                System.setProperty(entry.getKey(), entry.getValue());
            });
            System.out.println(">>> .env file loaded successfully. API Key found: " + (System.getProperty("GROQ_API_KEY") != null));
        } catch (Exception e) {
            System.err.println("!!! .env LOAD ERROR: " + e.getMessage());
        }

        SpringApplication.run(EventGuardApplication.class, args);
    }
}
