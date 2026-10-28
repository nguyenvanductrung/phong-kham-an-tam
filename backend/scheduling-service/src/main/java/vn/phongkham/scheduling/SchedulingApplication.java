package vn.phongkham.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@ComponentScan({"vn.phongkham.scheduling", "vn.phongkham.common"})
@EnableScheduling
public class SchedulingApplication {
    public static void main(String[] args) { SpringApplication.run(SchedulingApplication.class, args); }
    @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
}

