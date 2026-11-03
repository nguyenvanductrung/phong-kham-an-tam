package vn.phongkham.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.phongkham.common.HttpClientSupport;

@SpringBootApplication
@ComponentScan({"vn.phongkham.scheduling", "vn.phongkham.common"})
@EnableScheduling
public class SchedulingApplication {
    public static void main(String[] args) { SpringApplication.run(SchedulingApplication.class, args); }
    @Bean org.springframework.web.client.RestClient.Builder restClientBuilder() { return HttpClientSupport.restClientBuilder(); }
}

