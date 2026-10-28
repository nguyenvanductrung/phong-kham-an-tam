package vn.phongkham.clinical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@ComponentScan({"vn.phongkham.clinical", "vn.phongkham.common"})
@EnableScheduling
public class ClinicalApplication {
    public static void main(String[] args) { SpringApplication.run(ClinicalApplication.class, args); }
    @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
}

