package br.com.vagaviva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VagaVivaApplication {

    public static void main(String[] args) {
        SpringApplication.run(VagaVivaApplication.class, args);
    }
}
