package vn.phongkham.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import vn.phongkham.common.HttpClientSupport;

@SpringBootApplication
@ComponentScan({"vn.phongkham.patient", "vn.phongkham.common"})
public class PatientApplication {
    public static void main(String[] args) { SpringApplication.run(PatientApplication.class, args); }

    @Bean org.springframework.web.client.RestClient.Builder restClientBuilder() { return HttpClientSupport.restClientBuilder(); }
}

