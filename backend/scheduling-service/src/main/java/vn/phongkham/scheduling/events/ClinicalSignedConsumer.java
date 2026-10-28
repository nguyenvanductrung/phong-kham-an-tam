package vn.phongkham.scheduling.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="events.consumer.enabled",havingValue="true")
public class ClinicalSignedConsumer {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    public ClinicalSignedConsumer(JdbcTemplate jdbc,JsonMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

    @RabbitListener(queues="scheduling.events")
    @Transactional
    public void consume(String message) throws Exception {
        JsonNode event=mapper.readTree(message);
        if(!"ClinicalRecordSigned.v1".equals(event.path("eventType").asText())) return;
        UUID eventId=UUID.fromString(event.path("eventId").asText());
        UUID recordId=UUID.fromString(event.path("aggregateId").asText());
        long version=event.path("aggregateVersion").asLong();
        if(jdbc.update("INSERT INTO processed_events(event_id,aggregate_id,aggregate_version) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                eventId,recordId,version)==0) return;
        JsonNode payload=event.path("payload");
        UUID appointmentId=UUID.fromString(payload.path("appointmentId").asText());
        UUID patientId=UUID.fromString(payload.path("patientId").asText());
        UUID doctorId=UUID.fromString(payload.path("doctorId").asText());
        var row=jdbc.query("SELECT patient_id,doctor_id,status FROM appointments WHERE id=? FOR UPDATE",
                (rs,n)->new Appointment(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getString("status")),appointmentId)
                .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"APPOINTMENT_REFERENCE_INVALID","Lịch hẹn không tồn tại."));
        if(!row.patientId().equals(patientId)||!row.doctorId().equals(doctorId))
            throw new ApiException(HttpStatus.CONFLICT,"CLINICAL_EVENT_MISMATCH","Bệnh án đã chốt không khớp lịch hẹn.");
        if("IN_PROGRESS".equals(row.status()))
            jdbc.update("UPDATE appointments SET status='COMPLETED',completed_at=now(),updated_at=now(),version=version+1 WHERE id=?",appointmentId);
        else if(!"COMPLETED".equals(row.status()))
            throw new ApiException(HttpStatus.CONFLICT,"APPOINTMENT_NOT_IN_PROGRESS","Chỉ lượt khám đang thực hiện mới được hoàn tất khi chốt bệnh án.");
    }

    private record Appointment(UUID patientId,UUID doctorId,String status) {}
}
