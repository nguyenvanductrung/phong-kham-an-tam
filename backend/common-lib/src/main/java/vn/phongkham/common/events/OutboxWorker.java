package vn.phongkham.common.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="events.outbox.enabled",havingValue="true")
public class OutboxWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final JsonMapper mapper;
    public OutboxWorker(JdbcTemplate jdbc,TransactionTemplate transaction,RabbitTemplate rabbit,JsonMapper mapper){this.jdbc=jdbc;this.transaction=transaction;this.rabbit=rabbit;this.mapper=mapper;}

    @Scheduled(fixedDelayString="${events.outbox.poll-ms:1000}")
    public void publishBatch(){
        transaction.executeWithoutResult(status->{
            List<Row> rows=jdbc.query("""
                    SELECT o.event_id,o.event_type,o.schema_version,o.occurred_at,o.aggregate_id,o.aggregate_version,o.correlation_id,o.payload,o.attempts
                    FROM outbox_events o WHERE o.published_at IS NULL AND o.dead_lettered_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM outbox_events earlier WHERE earlier.aggregate_id=o.aggregate_id AND earlier.aggregate_version<o.aggregate_version AND earlier.published_at IS NULL AND earlier.dead_lettered_at IS NULL)
                    ORDER BY o.created_at LIMIT 20 FOR UPDATE OF o SKIP LOCKED
                    """,(rs,n)->new Row(rs.getObject("event_id",UUID.class),rs.getString("event_type"),rs.getInt("schema_version"),rs.getTimestamp("occurred_at").toInstant(),rs.getObject("aggregate_id",UUID.class),rs.getLong("aggregate_version"),rs.getString("correlation_id"),rs.getString("payload"),rs.getInt("attempts")));
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
            jdbc.update("UPDATE outbox_events SET published_at=now(),attempts=?,last_error=NULL WHERE event_id=?",attempt,row.eventId());
        }catch(Exception error){
            jdbc.update("UPDATE outbox_events SET attempts=?,last_error=? WHERE event_id=?",attempt,safe(error.getMessage()),row.eventId());
            if(attempt>=8) deadLetter(row,error);
        }
    }
    private void deadLetter(Row row,Exception cause){
        try{
            String body=mapper.writeValueAsString(new ClinicEvent(row.eventId(),row.eventType(),row.schemaVersion(),row.occurredAt(),row.aggregateId(),row.aggregateVersion(),row.correlationId(),mapper.readTree(row.payload())));
            CorrelationData confirm=new CorrelationData(row.eventId()+"-dlq");
            rabbit.convertAndSend("clinic.events.dlx","outbox.failed",body,message->{message.getMessageProperties().setMessageId(row.eventId().toString());message.getMessageProperties().setHeader("failure",safe(cause.getMessage()));return message;},confirm);
            if(confirm.getFuture().get(5,TimeUnit.SECONDS).isAck())jdbc.update("UPDATE outbox_events SET dead_lettered_at=now() WHERE event_id=?",row.eventId());
        }catch(Exception ignored){ }
    }
    private static String safe(String text){if(text==null)return "publish_failed";return text.length()>500?text.substring(0,500):text;}
    private record Row(UUID eventId,String eventType,int schemaVersion,Instant occurredAt,UUID aggregateId,long aggregateVersion,String correlationId,String payload,int attempts){}
}

