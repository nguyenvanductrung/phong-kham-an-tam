package vn.phongkham.notification.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import vn.phongkham.notification.persistence.MailJobWorkerDao;
import vn.phongkham.notification.persistence.MailJobWorkerDao.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

@Component
@ConditionalOnProperty(name="events.consumer.enabled",havingValue="true")
public class MailJobWorker {
    private final MailJobWorkerDao dao;private final TransactionTemplate tx;private final JavaMailSender mailSender;private final String from;
    public MailJobWorker(MailJobWorkerDao dao,TransactionTemplate tx,JavaMailSender mailSender,@org.springframework.beans.factory.annotation.Value("${app.mail.from}") String from){this.dao=dao;this.tx=tx;this.mailSender=mailSender;this.from=from;}
    @Scheduled(fixedDelayString="${notifications.worker.poll-ms:2000}")
    public void sendPending(){tx.executeWithoutResult(status->{
        List<Job> jobs=dao.findPendingJobs();
        for(Job job:jobs)send(job);
    });}
    private void send(Job job){
        int attempts=job.attempts()+1;
        try{
            SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(job.recipient());message.setSubject(job.subject());message.setText(job.body());mailSender.send(message);
            dao.recordSuccess(job.id(), attempts);
            dao.markJobSent(attempts, job.id());
            dao.markNotificationSent(job.notificationId());
        }catch(Exception ex){
            String error=ex.getMessage()==null?"mail_send_failed":ex.getMessage().substring(0,Math.min(500,ex.getMessage().length()));
            String next=attempts>=8?"DEAD":"PENDING";
            dao.recordFailure(job.id(), attempts, next, error);
            int delaySeconds=(int)Math.min(3600,5L*(1L<<Math.min(attempts-1,10)));
            dao.retryJob(next, attempts, error, delaySeconds, job.id());
            dao.markNotificationFailed(attempts>=8?"FAILED":"QUEUED", error, job.notificationId());
        }
    }

}

