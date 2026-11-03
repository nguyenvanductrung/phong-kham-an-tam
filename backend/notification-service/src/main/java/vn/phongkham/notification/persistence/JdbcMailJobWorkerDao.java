package vn.phongkham.notification.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.notification.persistence.MailJobWorkerDao.*;

@Repository
public class JdbcMailJobWorkerDao implements MailJobWorkerDao {
    private final JdbcTemplate jdbc;
    public JdbcMailJobWorkerDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Job> findPendingJobs() {
        return jdbc.query("SELECT id,notification_id,recipient,subject,body,attempts FROM notification_jobs WHERE status='PENDING' AND next_attempt_at<=now() ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED", (rs,n)->new Job(rs.getObject("id",UUID.class),rs.getObject("notification_id",UUID.class),rs.getString("recipient"),rs.getString("subject"),rs.getString("body"),rs.getInt("attempts")), new Object[]{});
    }

    @Override
    public int recordSuccess(UUID job, int attempt) {
        return jdbc.update("INSERT INTO notification_attempts(job_id,attempt_number,outcome) VALUES (?,?,'SENT')", new Object[]{job, attempt});
    }

    @Override
    public int markJobSent(int attempts, UUID job) {
        return jdbc.update("UPDATE notification_jobs SET status='SENT',attempts=?,sent_at=now(),last_error=NULL WHERE id=?", new Object[]{attempts, job});
    }

    @Override
    public int markNotificationSent(UUID notification) {
        return jdbc.update("UPDATE notifications SET status='SENT',sent_at=now() WHERE id=?", new Object[]{notification});
    }

    @Override
    public int recordFailure(UUID job, int attempts, String outcome, String error) {
        return jdbc.update("INSERT INTO notification_attempts(job_id,attempt_number,outcome,error_message) VALUES (?,?,?,?)", new Object[]{job, attempts, outcome, error});
    }

    @Override
    public int retryJob(String status, int attempts, String error, int delaySeconds, UUID job) {
        return jdbc.update("UPDATE notification_jobs SET status=?,attempts=?,last_error=?,next_attempt_at=now()+(? * INTERVAL '1 second') WHERE id=?", new Object[]{status, attempts, error, delaySeconds, job});
    }

    @Override
    public int markNotificationFailed(String status, String error, UUID notification) {
        return jdbc.update("UPDATE notifications SET status=?,error_message=? WHERE id=?", new Object[]{status, error, notification});
    }
}
