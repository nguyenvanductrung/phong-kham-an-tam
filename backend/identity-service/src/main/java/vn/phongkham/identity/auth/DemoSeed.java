package vn.phongkham.identity.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@Configuration
@Profile("dev")
public class DemoSeed {
    @Bean
    ApplicationRunner seedDemoUsers(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                    @Value("${app.demo-password:}") String demoPassword) {
        return args -> {
            if (demoPassword.isBlank()) throw new IllegalStateException("DEMO_LOGIN_PASSWORD is required in dev profile");
            jdbc.update("INSERT INTO roles(code,name) VALUES ('SUPER_ADMIN','Quản trị hệ thống'),('CLINIC_MANAGER','Quản lý'),('DOCTOR','Bác sĩ'),('RECEPTIONIST','Lễ tân'),('ASSISTANT','Phụ tá'),('PATIENT','Bệnh nhân') ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO permissions(code,description) VALUES ('patient:read','Xem hồ sơ hành chính'),('appointment:write','Quản lý lịch hẹn'),('clinical:write','Ghi bệnh án'),('clinical:sign','Chốt bệnh án') ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO role_permissions(role_code,permission_code) VALUES ('RECEPTIONIST','patient:read'),('RECEPTIONIST','appointment:write'),('DOCTOR','patient:read'),('DOCTOR','clinical:write'),('DOCTOR','clinical:sign'),('CLINIC_MANAGER','patient:read'),('CLINIC_MANAGER','appointment:write') ON CONFLICT DO NOTHING");
            addUser(jdbc, passwordEncoder, "receptionist@antam.local", "Lễ tân demo", "RECEPTIONIST", demoPassword);
            addUser(jdbc, passwordEncoder, "doctor@antam.local", "Bác sĩ demo", "DOCTOR", demoPassword);
            addUser(jdbc, passwordEncoder, "patient@antam.local", "Bệnh nhân demo", "PATIENT", demoPassword);
            addUser(jdbc, passwordEncoder, "manager@antam.local", "Quản lý demo", "CLINIC_MANAGER", demoPassword);
        };
    }

    private void addUser(JdbcTemplate jdbc, PasswordEncoder encoder, String email, String fullName, String role, String rawPassword) {
        jdbc.update("INSERT INTO users(email,password_hash,full_name) VALUES (?,?,?) ON CONFLICT (lower(email)) DO NOTHING", email, encoder.encode(rawPassword), fullName);
            UUID id = UUID.nameUUIDFromBytes(email.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jdbc.update("UPDATE users SET id=? WHERE lower(email)=lower(?) AND id<>?", id, email, id);
        jdbc.update("INSERT INTO user_roles(user_id,role_code) VALUES (?,?) ON CONFLICT DO NOTHING", id, role);
    }
}

