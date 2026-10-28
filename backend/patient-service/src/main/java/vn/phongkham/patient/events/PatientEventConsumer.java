package vn.phongkham.patient.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="events.consumer.enabled",havingValue="true")
public class PatientEventConsumer {
    private static final Set<String> SUPPORTED = Set.of("AppointmentBooked.v1", "AppointmentRescheduled.v1",
            "AppointmentCancelled.v1", "PatientCheckedIn.v1", "AppointmentNoShow.v1");
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    public PatientEventConsumer(JdbcTemplate jdbc,JsonMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    @RabbitListener(queues="patient.events")
    @Transactional
    public void consume(String message) throws Exception {
        JsonNode event=mapper.readTree(message);
        UUID eventId=UUID.fromString(event.path("eventId").asText());
        UUID aggregateId=UUID.fromString(event.path("aggregateId").asText());
        long version=event.path("aggregateVersion").asLong();
        if(jdbc.update("INSERT INTO processed_events(event_id,aggregate_id,aggregate_version) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                eventId,aggregateId,version)==0)return;
        String type=event.path("eventType").asText();
        if(!SUPPORTED.contains(type))return;
        JsonNode payload=event.path("payload");
        UUID patientId=UUID.fromString(payload.path("patientId").asText());
        UUID doctorId=UUID.fromString(payload.path("doctorId").asText());
        UUID appointmentId=UUID.fromString(payload.path("appointmentId").asText());
        if(type.equals("AppointmentCancelled.v1")||type.equals("AppointmentNoShow.v1")) {
            jdbc.update("""
                    INSERT INTO patient_access_grants(patient_id,doctor_id,appointment_id,expires_at,aggregate_version,revoked_at)
                    VALUES (?,?,?,now()+INTERVAL '1 second',?,now())
                    ON CONFLICT (patient_id,doctor_id,appointment_id) DO UPDATE
                    SET revoked_at=now(),aggregate_version=EXCLUDED.aggregate_version
                    WHERE patient_access_grants.aggregate_version<EXCLUDED.aggregate_version
                    """,patientId,doctorId,appointmentId,version);
            return;
        }
        Instant expiresAt=type.equals("PatientCheckedIn.v1")
                ? Instant.now().plus(24,ChronoUnit.HOURS)
                : Instant.parse(payload.path("scheduledAt").asText()).plus(payload.path("durationMinutes").asLong()+24,ChronoUnit.HOURS);
        jdbc.update("""
                INSERT INTO patient_access_grants(patient_id,doctor_id,appointment_id,expires_at,aggregate_version)
                VALUES (?,?,?,?,?)
                ON CONFLICT (patient_id,doctor_id,appointment_id) DO UPDATE
                SET expires_at=EXCLUDED.expires_at,aggregate_version=EXCLUDED.aggregate_version,revoked_at=NULL
                WHERE patient_access_grants.aggregate_version<EXCLUDED.aggregate_version
                """,patientId,doctorId,appointmentId,expiresAt.atOffset(java.time.ZoneOffset.UTC),version);
    }
}
