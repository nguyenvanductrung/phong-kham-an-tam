package vn.phongkham.patient.events;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class PatientEventConsumerIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");
    static JdbcTemplate jdbc;
    static PatientEventConsumer consumer;

    private static final UUID PATIENT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID DOCTOR = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID APPOINTMENT = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        consumer = new PatientEventConsumer(jdbc, JsonMapper.builder().build());
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE processed_events, patient_access_grants, patients CASCADE");
        jdbc.update("INSERT INTO patients(id,patient_code,full_name,phone) VALUES (?, 'PT-TEST', 'Test Patient', '0900000000')", PATIENT);
    }

    @Test
    void inboxDeduplicatesEventsAndOlderAppointmentEventsCannotRegrantAccess() throws Exception {
        String cancelled = event(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"), "AppointmentCancelled.v1", 2);
        String booked = event(UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"), "AppointmentBooked.v1", 1);

        consumer.consume(cancelled);
        consumer.consume(booked);
        consumer.consume(booked);

        var grant = jdbc.queryForMap("SELECT aggregate_version,revoked_at FROM patient_access_grants WHERE patient_id=? AND doctor_id=? AND appointment_id=?", PATIENT, DOCTOR, APPOINTMENT);
        assertEquals(2L, ((Number) grant.get("aggregate_version")).longValue());
        assertNotNull(grant.get("revoked_at"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM patient_access_grants", Integer.class));
    }

    private static String event(UUID eventId, String eventType, long version) throws Exception {
        return JsonMapper.builder().build().writeValueAsString(Map.of(
                "eventId", eventId,
                "eventType", eventType,
                "schemaVersion", 1,
                "occurredAt", "2030-01-01T00:00:00Z",
                "aggregateId", APPOINTMENT,
                "aggregateVersion", version,
                "correlationId", "patient-event-test",
                "payload", Map.of(
                        "appointmentId", APPOINTMENT,
                        "patientId", PATIENT,
                        "doctorId", DOCTOR,
                        "scheduledAt", "2030-01-02T03:00:00Z",
                        "durationMinutes", 30
                )
        ));
    }
}
