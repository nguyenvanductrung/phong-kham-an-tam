package vn.phongkham.patient.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import vn.phongkham.patient.persistence.PatientEventConsumerDao;
import vn.phongkham.patient.persistence.PatientEventConsumerDao.*;
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
    private final PatientEventConsumerDao dao;
    private final JsonMapper mapper;

    public PatientEventConsumer(PatientEventConsumerDao dao,JsonMapper mapper){this.dao=dao;this.mapper=mapper;}

    @RabbitListener(queues="patient.events")
    @Transactional
    public void consume(String message) throws Exception {
        JsonNode event=mapper.readTree(message);
        UUID eventId=UUID.fromString(event.path("eventId").asText());
        UUID aggregateId=UUID.fromString(event.path("aggregateId").asText());
        long version=event.path("aggregateVersion").asLong();
        if(dao.recordEvent(eventId, aggregateId, version)==0)return;
        String type=event.path("eventType").asText();
        if(!SUPPORTED.contains(type))return;
        JsonNode payload=event.path("payload");
        UUID patientId=UUID.fromString(payload.path("patientId").asText());
        UUID doctorId=UUID.fromString(payload.path("doctorId").asText());
        UUID appointmentId=UUID.fromString(payload.path("appointmentId").asText());
        if(type.equals("AppointmentCancelled.v1")||type.equals("AppointmentNoShow.v1")) {
            dao.revokeGrant(patientId, doctorId, appointmentId, version);
            return;
        }
        Instant expiresAt=type.equals("PatientCheckedIn.v1")
                ? Instant.now().plus(24,ChronoUnit.HOURS)
                : Instant.parse(payload.path("scheduledAt").asText()).plus(payload.path("durationMinutes").asLong()+24,ChronoUnit.HOURS);
        dao.upsertGrant(patientId, doctorId, appointmentId, expiresAt.atOffset(java.time.ZoneOffset.UTC), version);
    }
}
