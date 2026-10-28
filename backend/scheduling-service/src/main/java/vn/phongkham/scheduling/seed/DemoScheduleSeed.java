package vn.phongkham.scheduling.seed;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Configuration @Profile("dev")
public class DemoScheduleSeed {
    @Bean ApplicationRunner seedScheduling(JdbcTemplate jdbc){return args->{
        UUID doctor=UUID.nameUUIDFromBytes("doctor@antam.local".getBytes(StandardCharsets.UTF_8));
        UUID chair1=UUID.fromString("33333333-3333-4333-8333-333333333331"),chair2=UUID.fromString("33333333-3333-4333-8333-333333333332");
        jdbc.update("INSERT INTO clinic_settings(id,timezone,currency,no_show_grace_minutes) VALUES (1,'Asia/Ho_Chi_Minh','VND',15) ON CONFLICT (id) DO NOTHING");
        jdbc.update("INSERT INTO doctors(user_id,display_name,license_number,specialty) VALUES (?,?,?,?) ON CONFLICT (user_id) DO NOTHING",doctor,"BS. Trần Minh Quân","VN-DEMO-0001","Nha khoa tổng quát");
        jdbc.update("INSERT INTO chairs(id,name,room_type) VALUES (?, 'Ghế 01','EXAMINATION'),(?, 'Ghế 02','EXAMINATION') ON CONFLICT (id) DO NOTHING",chair1,chair2);
        jdbc.update("INSERT INTO services(id,code,name,description,price,duration_minutes) VALUES ('44444444-4444-4444-8444-444444444441','EXAM','Khám tổng quát','Khám và tư vấn răng miệng',150000,30),('44444444-4444-4444-8444-444444444442','CLEAN','Lấy cao răng','Vệ sinh răng miệng',350000,45),('44444444-4444-4444-8444-444444444443','FILL','Trám răng','Trám răng cơ bản',500000,60) ON CONFLICT (id) DO NOTHING");
        for(short day=1;day<=6;day++)jdbc.update("INSERT INTO doctor_working_hours(doctor_id,day_of_week,starts_at,ends_at) SELECT ?,?,'08:00','18:00' WHERE NOT EXISTS(SELECT 1 FROM doctor_working_hours WHERE doctor_id=? AND day_of_week=? AND effective_from=DATE '2000-01-01')",doctor,day,doctor,day);
    };}
}

