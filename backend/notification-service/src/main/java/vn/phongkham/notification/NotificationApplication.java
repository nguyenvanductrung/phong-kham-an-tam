package vn.phongkham.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan({"vn.phongkham.notification", "vn.phongkham.common"})
@EnableScheduling
public class NotificationApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationApplication.class,args); }
}

