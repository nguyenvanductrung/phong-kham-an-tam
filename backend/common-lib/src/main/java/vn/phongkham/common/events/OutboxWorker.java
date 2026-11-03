package vn.phongkham.common.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import vn.phongkham.common.persistence.OutboxWorkerDao;
import vn.phongkham.common.persistence.OutboxWorkerDao.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="events.outbox.enabled",havingValue="true")
public class OutboxWorker {
    private final OutboxWorkerDao dao;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final JsonMapper mapper;
    public OutboxWorker(OutboxWorkerDao dao,TransactionTemplate transaction,RabbitTemplate rabbit,JsonMapper mapper){this.dao=dao;this.transaction=transaction;this.rabbit=rabbit;this.mapper=mapper;}

    @Scheduled(fixedDelayString="${events.outbox.poll-ms:1000}")
    public void publishBatch(){
        transaction.executeWithoutResult(status->{
            List<Row> rows=dao.findPendingEvents();
            for(Row row:rows)send(row);
        });
    }

    private void send(Row row){
        int attempt=row.attempts()+1;
        try{
            JsonNode payload=mapper.readTree(row.payload());
            var event=new ClinicEvent(row.eventId(),row.eventType(),row.schemaVersion(),row.occurredAt(),row.aggregateId(),row.aggregateVersion(),row.correlationId(),payload);
            String body=mapper.writeValueAsString(event);
            CorrelationData confirm=new CorrelationData(row.eventId().toString());
            rabbit.convertAndSend("clinic.events",row.eventType(),body,message->{
                message.getMessageProperties().setMessageId(row.eventId().toString());
                message.getMessageProperties().setHeader("aggregateVersion",row.aggregateVersion());
                message.getMessageProperties().setHeader("correlationId",row.correlationId());
                return message;
            },confirm);
            var result=confirm.getFuture().get(5,TimeUnit.SECONDS);
            if(!result.isAck())throw new IllegalStateException("RabbitMQ rejected event: "+result.getReason());
            dao.markPublished(attempt, row.eventId());
        }catch(Exception error){
            dao.recordFailure(attempt, safe(error.getMessage()), row.eventId());
            if(attempt>=8) deadLetter(row,error);
        }
    }
    private void deadLetter(Row row,Exception cause){
        try{
            String body=mapper.writeValueAsString(new ClinicEvent(row.eventId(),row.eventType(),row.schemaVersion(),row.occurredAt(),row.aggregateId(),row.aggregateVersion(),row.correlationId(),mapper.readTree(row.payload())));
            CorrelationData confirm=new CorrelationData(row.eventId()+"-dlq");
            rabbit.convertAndSend("clinic.events.dlx","outbox.failed",body,message->{message.getMessageProperties().setMessageId(row.eventId().toString());message.getMessageProperties().setHeader("failure",safe(cause.getMessage()));return message;},confirm);
            if(confirm.getFuture().get(5,TimeUnit.SECONDS).isAck())dao.retryEvent(row.eventId());
        }catch(Exception ignored){ }
    }
    private static String safe(String text){if(text==null)return "publish_failed";return text.length()>500?text.substring(0,500):text;}

}

