package vn.phongkham.notification.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.notification.persistence.NotificationEventConsumerDao.*;

@Repository
public class JdbcNotificationEventConsumerDao implements NotificationEventConsumerDao {
    private final JdbcTemplate jdbc;
    public JdbcNotificationEventConsumerDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public int recordEvent(UUID event, UUID aggregate, long version) {
        return jdbc.update("INSERT INTO processed_events(event_id,aggregate_id,aggregate_version) VALUES (?,?,?) ON CONFLICT DO NOTHING", new Object[]{event, aggregate, version});
    }

    @Override
    public int insertNotification(UUID id, UUID patient, String subject, String body, String status, UUID event) {
        return jdbc.update("INSERT INTO notifications(id,patient_id,channel,template_code,title,body,status,idempotency_key) VALUES (?,?,'EMAIL','APPOINTMENT_BOOKED',?,?,?,?)", new Object[]{id, patient, subject, body, status, event});
    }

    @Override
    public int insertMailJob(UUID notification, UUID event, String recipient, String subject, String body) {
        return jdbc.update("INSERT INTO notification_jobs(notification_id,event_id,recipient,subject,body) VALUES (?,?,?,?,?)", new Object[]{notification, event, recipient, subject, body});
    }
}
