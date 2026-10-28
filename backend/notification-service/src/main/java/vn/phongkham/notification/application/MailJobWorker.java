package vn.phongkham.notification.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="events.consumer.enabled",havingValue="true")
public class MailJobWorker {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final JavaMailSender mailSender;private final String from;
    public MailJobWorker(JdbcTemplate jdbc,TransactionTemplate tx,JavaMailSender mailSender,@org.springframework.beans.factory.annotation.Value("${app.mail.from}") String from){this.jdbc=jdbc;this.tx=tx;this.mailSender=mailSender;this.from=from;}
    @Scheduled(fixedDelayString="${notifications.worker.poll-ms:2000}")
    public void sendPending(){tx.executeWithoutResult(status->{
        List<Job> jobs=jdbc.query("SELECT id,notification_id,recipient,subject,body,attempts FROM notification_jobs WHERE status='PENDING' AND next_attempt_at<=now() ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED",
                (rs,n)->new Job(rs.getObject("id",UUID.class),rs.getObject("notification_id",UUID.class),rs.getString("recipient"),rs.getString("subject"),rs.getString("body"),rs.getInt("attempts")));
        for(Job job:jobs)send(job);
    });}
    private void send(Job job){
        int attempts=job.attempts()+1;
        try{
            SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(job.recipient());message.setSubject(job.subject());message.setText(job.body());mailSender.send(message);
            jdbc.update("INSERT INTO notification_attempts(job_id,attempt_number,outcome) VALUES (?,?,'SENT')",job.id(),attempts);
            jdbc.update("UPDATE notification_jobs SET status='SENT',attempts=?,sent_at=now(),last_error=NULL WHERE id=?",attempts,job.id());
            jdbc.update("UPDATE notifications SET status='SENT',sent_at=now() WHERE id=?",job.notificationId());
        }catch(Exception ex){
            String error=ex.getMessage()==null?"mail_send_failed":ex.getMessage().substring(0,Math.min(500,ex.getMessage().length()));
            String next=attempts>=8?"DEAD":"PENDING";
            jdbc.update("INSERT INTO notification_attempts(job_id,attempt_number,outcome,error_message) VALUES (?,?,?,?)",job.id(),attempts,next,error);
            int delaySeconds=(int)Math.min(3600,5L*(1L<<Math.min(attempts-1,10)));
            jdbc.update("UPDATE notification_jobs SET status=?,attempts=?,last_error=?,next_attempt_at=now()+(? * INTERVAL '1 second') WHERE id=?",next,attempts,error,delaySeconds,job.id());
            jdbc.update("UPDATE notifications SET status=?,error_message=? WHERE id=?",attempts>=8?"FAILED":"QUEUED",error,job.notificationId());
        }
    }
    private record Job(UUID id,UUID notificationId,String recipient,String subject,String body,int attempts){}
}

