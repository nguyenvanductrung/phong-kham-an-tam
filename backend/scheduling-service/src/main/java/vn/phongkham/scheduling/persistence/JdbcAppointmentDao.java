package vn.phongkham.scheduling.persistence;

import java.time.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.phongkham.scheduling.api.SchedulingModels;
import java.sql.PreparedStatement;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.scheduling.persistence.AppointmentDao.*;

@Repository
public class JdbcAppointmentDao implements AppointmentDao {
    private final JdbcTemplate jdbc;
    public JdbcAppointmentDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockResourceKeys(String... keys) {
        for(String key:keys) jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(PreparedStatement statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")){
                statement.setString(1,key);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public List<ServiceSnapshot> findActiveService(UUID serviceId) {
        return jdbc.query("SELECT id,name,price,duration_minutes FROM services WHERE id=? AND is_active", (rs,n)->new ServiceSnapshot(rs.getObject("id",UUID.class),rs.getString("name"),rs.getBigDecimal("price"),rs.getInt("duration_minutes")), new Object[]{serviceId});
    }

    @Override
    public int reserveRequest(UUID actor, String key, byte[] hash) {
        return jdbc.update("INSERT INTO idempotency_records(actor_id,idempotency_key,request_hash) VALUES (?,?,?) ON CONFLICT DO NOTHING", new Object[]{actor, key, hash});
    }

    @Override
    public int insertAppointment(UUID id, String number, UUID patient, UUID doctor, UUID chair, OffsetDateTime startsAt, int duration, String reason, String note, UUID actor, String source) {
        return jdbc.update("INSERT INTO appointments(id,appointment_number,patient_id,doctor_id,chair_id,scheduled_at,duration_minutes,reason,patient_note,created_by,source) VALUES (?,?,?,?,?,?,?,?,?,?,?)", new Object[]{id, number, patient, doctor, chair, startsAt, duration, reason, note, actor, source});
    }

    @Override
    public int insertServiceSnapshot(UUID appointment, UUID service, String title, BigDecimal price) {
        return jdbc.update("INSERT INTO appointment_services(appointment_id,service_id,title_snapshot,estimated_unit_price) VALUES (?,?,?,?)", new Object[]{appointment, service, title, price});
    }

    @Override
    public int attachRequest(UUID appointment, UUID actor, String key) {
        return jdbc.update("UPDATE idempotency_records SET appointment_id=? WHERE actor_id=? AND idempotency_key=?", new Object[]{appointment, actor, key});
    }

    @Override
    public List<AppointmentRow> findLockedAppointment(UUID id) {
        return jdbc.query("SELECT id,patient_id,doctor_id,scheduled_at,status,version FROM appointments WHERE id=? FOR UPDATE", (rs,n)->new AppointmentRow(rs.getObject("id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getTimestamp("scheduled_at").toInstant(),rs.getString("status"),rs.getLong("version")), new Object[]{id});
    }

    @Override
    public Integer getNoShowGrace() {
        return jdbc.queryForObject("SELECT no_show_grace_minutes FROM clinic_settings WHERE id=1", Integer.class, new Object[]{});
    }

    @Override
    public int cancelAppointment(String status, String reason, UUID id, long version) {
        return jdbc.update("UPDATE appointments SET status=?, cancellation_reason=?, cancelled_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=?", new Object[]{status, reason, id, version});
    }

    @Override
    public int checkInAppointment(String status, UUID id, long version) {
        return jdbc.update("UPDATE appointments SET status=?,checked_in_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=?", new Object[]{status, id, version});
    }

    @Override
    public int startAppointment(String status, UUID id, long version) {
        return jdbc.update("UPDATE appointments SET status=?,started_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=?", new Object[]{status, id, version});
    }

    @Override
    public int changeStatus(String status, UUID id, long version) {
        return jdbc.update("UPDATE appointments SET status=?,updated_at=now(),version=version+1 WHERE id=? AND version=?", new Object[]{status, id, version});
    }

    @Override
    public int insertRequest(UUID actor, String key, byte[] hash, UUID appointment) {
        return jdbc.update("INSERT INTO idempotency_records(actor_id,idempotency_key,request_hash,appointment_id) VALUES (?,?,?,?)", new Object[]{actor, key, hash, appointment});
    }

    @Override
    public List<ResourceRow> findResources(UUID id) {
        return jdbc.query("SELECT patient_id,doctor_id FROM appointments WHERE id=?", (rs,n)->new ResourceRow(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class)), new Object[]{id});
    }

    @Override
    public List<RescheduleRow> findLockedReschedule(UUID id) {
        return jdbc.query("SELECT patient_id,doctor_id,chair_id,status,version FROM appointments WHERE id=? FOR UPDATE", (rs,n)->new RescheduleRow(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getObject("chair_id",UUID.class),rs.getString("status"),rs.getLong("version")), new Object[]{id});
    }

    @Override
    public int rescheduleAppointment(UUID chair, OffsetDateTime startsAt, int duration, UUID id, long version) {
        return jdbc.update("UPDATE appointments SET chair_id=?,scheduled_at=?,duration_minutes=?,updated_at=now(),version=version+1 WHERE id=? AND version=?", new Object[]{chair, startsAt, duration, id, version});
    }

    @Override
    public int insertRescheduleRequest(UUID actor, String key, byte[] hash, UUID appointment) {
        return jdbc.update("INSERT INTO idempotency_records(actor_id,idempotency_key,request_hash,appointment_id) VALUES (?,?,?,?)", new Object[]{actor, key, hash, appointment});
    }

    @Override
    public List<SchedulingModels.AppointmentResponse> listAppointments(OffsetDateTime from, OffsetDateTime until, UUID doctorFilter, UUID doctor, UUID patientFilter, UUID patient) {
        return jdbc.query("""
                SELECT a.id,a.appointment_number,a.patient_id,a.doctor_id,a.chair_id,a.scheduled_at,a.duration_minutes,a.status,a.source,a.version,
                       s.title_snapshot,s.estimated_unit_price
                FROM appointments a LEFT JOIN appointment_services s ON s.appointment_id=a.id
                WHERE a.scheduled_at>=? AND a.scheduled_at<? AND (?::uuid IS NULL OR a.doctor_id=?) AND (?::uuid IS NULL OR a.patient_id=?)
                ORDER BY a.scheduled_at LIMIT 500
                """, (rs,n)->map(rs), new Object[]{from, until, doctorFilter, doctor, patientFilter, patient});
    }

    @Override
    public List<SchedulingModels.AppointmentResponse> listHistory(UUID patient, UUID doctorFilter, UUID doctor, int limit) {
        return jdbc.query("""
                SELECT a.id,a.appointment_number,a.patient_id,a.doctor_id,a.chair_id,a.scheduled_at,a.duration_minutes,a.status,a.source,a.version,
                       s.title_snapshot,s.estimated_unit_price
                FROM appointments a LEFT JOIN appointment_services s ON s.appointment_id=a.id
                WHERE a.patient_id=? AND (?::uuid IS NULL OR a.doctor_id=?)
                ORDER BY a.scheduled_at DESC,a.id DESC LIMIT ?
                """, (rs, n) -> map(rs), new Object[]{patient, doctorFilter, doctor, limit});
    }

    @Override
    public List<SchedulingModels.ServiceItem> listActiveServices() {
        return jdbc.query("SELECT id,code,name,price,duration_minutes FROM services WHERE is_active ORDER BY name", (rs,n)->new SchedulingModels.ServiceItem(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getBigDecimal("price"),rs.getInt("duration_minutes")), new Object[]{});
    }

    @Override
    public List<SchedulingModels.DoctorItem> listBookableDoctors() {
        return jdbc.query("SELECT user_id,display_name,specialty FROM doctors WHERE is_bookable ORDER BY display_name", (rs,n)->new SchedulingModels.DoctorItem(rs.getObject("user_id",UUID.class),rs.getString("display_name"),rs.getString("specialty")), new Object[]{});
    }

    @Override
    public List<SchedulingModels.ChairItem> listActiveChairs() {
        return jdbc.query("SELECT id,name,room_type FROM chairs WHERE is_active ORDER BY name", (rs,n)->new SchedulingModels.ChairItem(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("room_type")), new Object[]{});
    }

    @Override
    public Boolean isDoctorBookable(UUID doctor) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=? AND is_bookable)", Boolean.class, new Object[]{doctor});
    }

    @Override
    public List<LocalTime[]> findAvailabilityHours(UUID doctor, int weekday, LocalDate day) {
        return jdbc.query("SELECT starts_at,ends_at FROM doctor_working_hours WHERE doctor_id=? AND day_of_week=? AND ? BETWEEN effective_from AND COALESCE(effective_until,DATE '9999-12-31') ORDER BY starts_at", (rs,n)->new LocalTime[]{rs.getTime("starts_at").toLocalTime(),rs.getTime("ends_at").toLocalTime()}, new Object[]{doctor, weekday, day});
    }

    @Override
    public Integer lockSettings() {
        return jdbc.queryForObject("SELECT id FROM clinic_settings WHERE id=1 FOR SHARE", Integer.class, new Object[]{});
    }

    @Override
    public Boolean isDoctorAvailable(UUID doctor) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=? AND is_bookable)", Boolean.class, new Object[]{doctor});
    }

    @Override
    public Boolean isChairAvailable(UUID chair) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chairs WHERE id=? AND is_active)", Boolean.class, new Object[]{chair});
    }

    @Override
    public List<LocalTime[]> findWorkingHours(UUID doctor, int weekday, LocalDate day) {
        return jdbc.query("SELECT starts_at,ends_at FROM doctor_working_hours WHERE doctor_id=? AND day_of_week=? AND ? BETWEEN effective_from AND COALESCE(effective_until,DATE '9999-12-31')", (rs,n)->new LocalTime[]{rs.getTime("starts_at").toLocalTime(),rs.getTime("ends_at").toLocalTime()}, new Object[]{doctor, weekday, day});
    }

    @Override
    public Boolean hasClosure(OffsetDateTime from, OffsetDateTime until) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clinic_closures WHERE tstzrange(starts_at,ends_at,'[)') && tstzrange(?,?,'[)'))", Boolean.class, new Object[]{from, until});
    }

    @Override
    public Boolean hasLeave(UUID doctor, OffsetDateTime from, OffsetDateTime until) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctor_leave WHERE doctor_id=? AND status='APPROVED' AND tstzrange(starts_at,ends_at,'[)') && tstzrange(?,?,'[)'))", Boolean.class, new Object[]{doctor, from, until});
    }

    @Override
    public Boolean overlaps(UUID doctor, UUID chair, OffsetDateTime from, OffsetDateTime until) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments WHERE status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND (doctor_id=? OR chair_id=?) AND during && tstzrange(?,?,'[)'))", Boolean.class, new Object[]{doctor, chair, from, until});
    }

    @Override
    public List<Replay> findReplay(UUID actor, String key) {
        return jdbc.query("SELECT request_hash,appointment_id FROM idempotency_records WHERE actor_id=? AND idempotency_key=? FOR UPDATE", (rs,n)->new Replay(rs.getBytes("request_hash"),rs.getObject("appointment_id",UUID.class)), new Object[]{actor, key});
    }

    @Override
    public List<SchedulingModels.AppointmentResponse> findAppointment(UUID id) {
        return jdbc.query("""
                SELECT a.id,a.appointment_number,a.patient_id,a.doctor_id,a.chair_id,a.scheduled_at,a.duration_minutes,a.status,a.source,a.version,
                       s.title_snapshot,s.estimated_unit_price
                FROM appointments a LEFT JOIN appointment_services s ON s.appointment_id=a.id WHERE a.id=?
                """, (rs,n)->map(rs), new Object[]{id});
    }

    @Override
    public int insertAudit(UUID actor, String label, String action, String entityType, UUID entity, String trace, String metadata) {
        return jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?,?,?,?,?,?,?::jsonb)", new Object[]{actor, label, action, entityType, entity, trace, metadata});
    }

    @Override
    public int insertOutbox(UUID eventId, String eventType, OffsetDateTime occurredAt, UUID aggregate, long version, String trace, String payload) {
        return jdbc.update("INSERT INTO outbox_events(event_id,event_type,schema_version,occurred_at,aggregate_id,aggregate_version,correlation_id,payload) VALUES (?,?,1,?,?,?,?,?::jsonb)", new Object[]{eventId, eventType, occurredAt, aggregate, version, trace, payload});
    }

    private SchedulingModels.AppointmentResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SchedulingModels.AppointmentResponse(rs.getObject("id",UUID.class),rs.getString("appointment_number"),rs.getObject("patient_id",UUID.class),
                rs.getObject("doctor_id",UUID.class),rs.getObject("chair_id",UUID.class),rs.getTimestamp("scheduled_at").toInstant(),rs.getInt("duration_minutes"),
                rs.getString("status"),rs.getString("source"),rs.getString("title_snapshot"),rs.getBigDecimal("estimated_unit_price"),rs.getLong("version"));
    }
}
