package vn.phongkham.clinical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.phongkham.common.HttpClientSupport;

@SpringBootApplication
@ComponentScan({"vn.phongkham.clinical", "vn.phongkham.common"})
@EnableScheduling
public class ClinicalApplication {
    public static void main(String[] args) { SpringApplication.run(ClinicalApplication.class, args); }
    @Bean org.springframework.web.client.RestClient.Builder restClientBuilder() { return HttpClientSupport.restClientBuilder(); }
}

