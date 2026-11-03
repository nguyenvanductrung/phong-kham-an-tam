package vn.phongkham.notification.persistence;

import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcNotificationEventConsumerDao. */
public interface NotificationEventConsumerDao {
    int recordEvent(UUID event, UUID aggregate, long version);
    int insertNotification(UUID id, UUID patient, String subject, String body, String status, UUID event);
    int insertMailJob(UUID notification, UUID event, String recipient, String subject, String body);

}
