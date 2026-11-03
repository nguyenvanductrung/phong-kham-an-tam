package vn.phongkham.common.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.common.persistence.OutboxWorkerDao.*;

@Repository
public class JdbcOutboxWorkerDao implements OutboxWorkerDao {
    private final JdbcTemplate jdbc;
    public JdbcOutboxWorkerDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Row> findPendingEvents() {
        return jdbc.query("""
                    SELECT o.event_id,o.event_type,o.schema_version,o.occurred_at,o.aggregate_id,o.aggregate_version,o.correlation_id,o.payload,o.attempts
                    FROM outbox_events o WHERE o.published_at IS NULL AND o.dead_lettered_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM outbox_events earlier WHERE earlier.aggregate_id=o.aggregate_id AND earlier.aggregate_version<o.aggregate_version AND earlier.published_at IS NULL AND earlier.dead_lettered_at IS NULL)
                    ORDER BY o.created_at LIMIT 20 FOR UPDATE OF o SKIP LOCKED
                    """, (rs,n)->new Row(rs.getObject("event_id",UUID.class),rs.getString("event_type"),rs.getInt("schema_version"),rs.getTimestamp("occurred_at").toInstant(),rs.getObject("aggregate_id",UUID.class),rs.getLong("aggregate_version"),rs.getString("correlation_id"),rs.getString("payload"),rs.getInt("attempts")), new Object[]{});
    }

    @Override
    public int markPublished(int attempts, UUID event) {
        return jdbc.update("UPDATE outbox_events SET published_at=now(),attempts=?,last_error=NULL WHERE event_id=?", new Object[]{attempts, event});
    }

    @Override
    public int recordFailure(int attempts, String error, UUID event) {
        return jdbc.update("UPDATE outbox_events SET attempts=?,last_error=? WHERE event_id=?", new Object[]{attempts, error, event});
    }

    @Override
    public int retryEvent(UUID event) {
        return jdbc.update("UPDATE outbox_events SET dead_lettered_at=now() WHERE event_id=?", new Object[]{event});
    }
}
