package vn.phongkham.notification.events;

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

@Testcontainers(disabledWithoutDocker = true)
class NotificationEventConsumerIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");
    static JdbcTemplate jdbc;
    static NotificationEventConsumer consumer;

    private static final UUID PATIENT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPOINTMENT = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID EVENT = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        consumer = new NotificationEventConsumer(jdbc, JsonMapper.builder().build(), "noreply@antam.local");
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE notification_attempts, notification_jobs, notifications, processed_events CASCADE");
    }

    @Test
    void duplicateBookingEventCreatesOnlyOneNotificationAndOneMailJob() throws Exception {
        String event = JsonMapper.builder().build().writeValueAsString(Map.of(
                "eventId", EVENT,
                "eventType", "AppointmentBooked.v1",
                "schemaVersion", 1,
                "occurredAt", "2030-01-01T00:00:00Z",
                "aggregateId", APPOINTMENT,
                "aggregateVersion", 1,
                "correlationId", "notification-event-test",
                "payload", Map.of(
                        "appointmentId", APPOINTMENT,
                        "patientId", PATIENT,
                        "patientEmail", "patient@example.test",
                        "scheduledAt", "2030-01-02T03:00:00Z"
                )
        ));

        consumer.consume(event);
        consumer.consume(event);

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE idempotency_key=?", Integer.class, EVENT));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_jobs WHERE event_id=?", Integer.class, EVENT));
    }
}
