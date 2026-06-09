package br.com.pinsaude.repasse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RepasseApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepasseApplication.class, args);
    }
}
