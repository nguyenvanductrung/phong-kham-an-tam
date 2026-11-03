package vn.phongkham.common.persistence;

import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcOutboxWorkerDao. */
public interface OutboxWorkerDao {
    List<Row> findPendingEvents();
    int markPublished(int attempts, UUID event);
    int recordFailure(int attempts, String error, UUID event);
    int retryEvent(UUID event);
    record Row(UUID eventId,String eventType,int schemaVersion,Instant occurredAt,UUID aggregateId,long aggregateVersion,String correlationId,String payload,int attempts){}
}
