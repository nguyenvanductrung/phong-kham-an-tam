package vn.phongkham.patient.seed;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Configuration
@Profile("dev")
public class DemoPatientSeed {
    @Bean ApplicationRunner seedPatient(JdbcTemplate jdbc, @Value("${app.demo-password:}") String demoPassword) {
        return args -> {
            UUID patientId = UUID.fromString("22222222-2222-4222-8222-222222222222");
            UUID accountId = UUID.nameUUIDFromBytes("patient@antam.local".getBytes(StandardCharsets.UTF_8));
            jdbc.update("""
                    INSERT INTO patients(id,patient_code,full_name,phone,email,gender,date_of_birth,allergies,medical_history)
                    VALUES (?, 'BN-DEMO-0001','Nguyễn Minh Anh','0900000001','patient@antam.local','FEMALE','1995-04-14','Dị ứng penicillin','Đang dùng thuốc huyết áp')
                    ON CONFLICT (id) DO NOTHING
                    """, patientId);
            jdbc.update("INSERT INTO patient_account_links(patient_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", patientId, accountId);
        };
    }
}

