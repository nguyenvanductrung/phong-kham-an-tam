package vn.phongkham.notification.persistence;

import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcMailJobWorkerDao. */
public interface MailJobWorkerDao {
    List<Job> findPendingJobs();
    int recordSuccess(UUID job, int attempt);
    int markJobSent(int attempts, UUID job);
    int markNotificationSent(UUID notification);
    int recordFailure(UUID job, int attempts, String outcome, String error);
    int retryJob(String status, int attempts, String error, int delaySeconds, UUID job);
    int markNotificationFailed(String status, String error, UUID notification);
    record Job(UUID id,UUID notificationId,String recipient,String subject,String body,int attempts){}
}
