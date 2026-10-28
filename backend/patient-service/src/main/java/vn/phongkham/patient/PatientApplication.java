package vn.phongkham.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"vn.phongkham.patient", "vn.phongkham.common"})
public class PatientApplication {
    public static void main(String[] args) { SpringApplication.run(PatientApplication.class, args); }

    @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
}

