package vn.phongkham.notification.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="events.consumer.enabled",havingValue="true")
public class NotificationEventConsumer {
    private final JdbcTemplate jdbc;private final JsonMapper mapper;private final String from;
    public NotificationEventConsumer(JdbcTemplate jdbc,JsonMapper mapper,@org.springframework.beans.factory.annotation.Value("${app.mail.from}") String from){this.jdbc=jdbc;this.mapper=mapper;this.from=from;}
    @RabbitListener(queues="notification.events")
    @Transactional
    public void consume(String message)throws Exception{
        JsonNode event=mapper.readTree(message);
        UUID eventId=UUID.fromString(event.path("eventId").asText());
        UUID aggregateId=UUID.fromString(event.path("aggregateId").asText());
        long version=event.path("aggregateVersion").asLong();
        if(jdbc.update("INSERT INTO processed_events(event_id,aggregate_id,aggregate_version) VALUES (?,?,?) ON CONFLICT DO NOTHING",eventId,aggregateId,version)==0)return;
        if(!"AppointmentBooked.v1".equals(event.path("eventType").asText()))return;
        JsonNode payload=event.path("payload");
        String recipient=payload.path("patientEmail").asText("");
        UUID patientId=UUID.fromString(payload.path("patientId").asText());
        String scheduled=payload.path("scheduledAt").asText();
        String localTime=DateTimeFormatter.ofPattern("dd/MM/yyyy 'lúc' HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.parse(scheduled));
        String subject="Xác nhận lịch hẹn tại Nha Khoa An Tâm";
        String body="Lịch khám của bạn đã được đặt vào "+localTime+". Nếu cần hỗ trợ, vui lòng liên hệ phòng khám.";
        UUID notificationId=UUID.randomUUID();
        String status=recipient.isBlank()?"SKIPPED":"QUEUED";
        jdbc.update("INSERT INTO notifications(id,patient_id,channel,template_code,title,body,status,idempotency_key) VALUES (?,?,'EMAIL','APPOINTMENT_BOOKED',?,?,?,?)",
                notificationId,patientId,subject,body,status,eventId);
        if(!recipient.isBlank())jdbc.update("INSERT INTO notification_jobs(notification_id,event_id,recipient,subject,body) VALUES (?,?,?,?,?)",notificationId,eventId,recipient,subject,body);
    }
}

