package vn.phongkham.scheduling.persistence;

import java.time.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.phongkham.scheduling.api.SchedulingAdminModels;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.scheduling.persistence.SchedulingAdminDao.*;

@Repository
public class JdbcSchedulingAdminDao implements SchedulingAdminDao {
    private final JdbcTemplate jdbc;
    public JdbcSchedulingAdminDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hasFutureChairAppointment(UUID chairId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments WHERE chair_id=? AND status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND scheduled_at>=now())", Boolean.class, chairId));
    }

    public boolean hasAppointmentOverlap(UUID doctorId, UUID chairId, Instant startsAt, Instant endsAt) {
        if (doctorId != null && chairId != null) throw new IllegalArgumentException("Only one resource filter is supported");
        String resourceCondition = doctorId != null ? "doctor_id=? AND " : chairId != null ? "chair_id=? AND " : "";
        Object[] args = doctorId != null ? new Object[]{doctorId, databaseTime(startsAt), databaseTime(endsAt)} : chairId != null ? new Object[]{chairId, databaseTime(startsAt), databaseTime(endsAt)} : new Object[]{databaseTime(startsAt), databaseTime(endsAt)};
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments WHERE " + resourceCondition +
                "status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND scheduled_at + duration_minutes*INTERVAL '1 minute' > now() " +
                "AND during && tstzrange(?,?,'[)'))", Boolean.class, args));
    }

    public boolean serviceExists(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM services WHERE id=?)", Boolean.class, id)); }

    public boolean chairExists(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chairs WHERE id=?)", Boolean.class, id)); }

    public boolean doctorExists(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=?)", Boolean.class, id)); }

    @Override
    public List<SchedulingAdminModels.ServiceItem> listServices() {
        return jdbc.query("SELECT id,code,name,description,price,duration_minutes,is_active FROM services ORDER BY name", (rs, n) -> new SchedulingAdminModels.ServiceItem(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("description"), rs.getBigDecimal("price"),
                        rs.getInt("duration_minutes"), rs.getBoolean("is_active")), new Object[]{});
    }

    @Override
    public List<SchedulingAdminModels.DoctorItem> listDoctors() {
        return jdbc.query("SELECT user_id,display_name,specialty,is_bookable FROM doctors ORDER BY display_name", (rs, n) -> new SchedulingAdminModels.DoctorItem(rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                        rs.getString("specialty"), rs.getBoolean("is_bookable")), new Object[]{});
    }

    @Override
    public List<SchedulingAdminModels.ChairItem> listChairs() {
        return jdbc.query("SELECT id,name,room_type,is_active FROM chairs ORDER BY name", (rs, n) -> new SchedulingAdminModels.ChairItem(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("room_type"), rs.getBoolean("is_active")), new Object[]{});
    }

    @Override
    public List<SchedulingAdminModels.WorkingHourItem> listWorkingHours() {
        return jdbc.query("SELECT h.id,h.doctor_id,d.display_name,h.day_of_week,h.starts_at,h.ends_at,h.effective_from,h.effective_until " +
                        "FROM doctor_working_hours h JOIN doctors d ON d.user_id=h.doctor_id ORDER BY d.display_name,h.day_of_week,h.starts_at", (rs, n) -> new SchedulingAdminModels.WorkingHourItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getString("display_name"), rs.getInt("day_of_week"), rs.getTime("starts_at").toLocalTime(),
                        rs.getTime("ends_at").toLocalTime(), rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class)), new Object[]{});
    }

    @Override
    public List<SchedulingAdminModels.LeaveItem> listLeaves() {
        return jdbc.query("SELECT l.id,l.doctor_id,d.display_name,l.starts_at,l.ends_at,l.status,l.reason " +
                        "FROM doctor_leave l JOIN doctors d ON d.user_id=l.doctor_id ORDER BY l.starts_at DESC LIMIT 200", (rs, n) -> new SchedulingAdminModels.LeaveItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getString("display_name"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                        rs.getString("status"), rs.getString("reason")), new Object[]{});
    }

    @Override
    public List<SchedulingAdminModels.ClosureItem> listClosures() {
        return jdbc.query("SELECT id,starts_at,ends_at,reason FROM clinic_closures ORDER BY starts_at DESC LIMIT 200", (rs, n) -> new SchedulingAdminModels.ClosureItem(rs.getObject("id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(), rs.getString("reason")), new Object[]{});
    }

    @Override
    public Boolean serviceCodeExists(String code, UUID excluded, UUID id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM services WHERE code=? AND (?::uuid IS NULL OR id<>?))", Boolean.class, new Object[]{code, excluded, id});
    }

    @Override
    public int insertService(UUID id, String code, String name, String description, BigDecimal price, int duration, boolean active) {
        return jdbc.update("INSERT INTO services(id,code,name,description,price,duration_minutes,is_active) VALUES (?,?,?,?,?,?,?)", new Object[]{id, code, name, description, price, duration, active});
    }

    @Override
    public int updateService(String code, String name, String description, BigDecimal price, int duration, boolean active, UUID id) {
        return jdbc.update("UPDATE services SET code=?,name=?,description=?,price=?,duration_minutes=?,is_active=?,updated_at=now() WHERE id=?", new Object[]{code, name, description, price, duration, active, id});
    }

    @Override
    public SchedulingAdminModels.ServiceItem findService(UUID id) {
        return jdbc.queryForObject("SELECT id,code,name,description,price,duration_minutes,is_active FROM services WHERE id=?", (rs, n) -> new SchedulingAdminModels.ServiceItem(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"), rs.getInt("duration_minutes"), rs.getBoolean("is_active")), new Object[]{id});
    }

    @Override
    public Boolean chairNameExists(String name, UUID excluded, UUID id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chairs WHERE lower(name)=lower(?) AND (?::uuid IS NULL OR id<>?))", Boolean.class, new Object[]{name, excluded, id});
    }

    @Override
    public int insertChair(UUID id, String name, String roomType, boolean active) {
        return jdbc.update("INSERT INTO chairs(id,name,room_type,is_active) VALUES (?,?,?,?)", new Object[]{id, name, roomType, active});
    }

    @Override
    public int updateChair(String name, String roomType, boolean active, UUID id) {
        return jdbc.update("UPDATE chairs SET name=?,room_type=?,is_active=? WHERE id=?", new Object[]{name, roomType, active, id});
    }

    @Override
    public Boolean workingHoursOverlap(UUID doctor, int weekday, LocalTime until, LocalTime from, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctor_working_hours " +
                "WHERE doctor_id=? AND day_of_week=? AND starts_at<? AND ends_at>? " +
                "AND daterange(effective_from,COALESCE(effective_until,DATE 'infinity'),'[]') && daterange(?,COALESCE(?::date,DATE 'infinity'),'[]'))", Boolean.class, new Object[]{doctor, weekday, until, from, effectiveFrom, effectiveUntil});
    }

    @Override
    public int insertWorkingHour(UUID id, UUID doctor, int weekday, LocalTime from, LocalTime until, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        return jdbc.update("INSERT INTO doctor_working_hours(id,doctor_id,day_of_week,starts_at,ends_at,effective_from,effective_until) VALUES (?,?,?,?,?,?,?)", new Object[]{id, doctor, weekday, from, until, effectiveFrom, effectiveUntil});
    }

    @Override
    public SchedulingAdminModels.WorkingHourItem findWorkingHour(UUID id) {
        return jdbc.queryForObject("SELECT h.id,h.doctor_id,d.display_name,h.day_of_week,h.starts_at,h.ends_at,h.effective_from,h.effective_until " +
                        "FROM doctor_working_hours h JOIN doctors d ON d.user_id=h.doctor_id WHERE h.id=?", (rs, n) -> new SchedulingAdminModels.WorkingHourItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getString("display_name"), rs.getInt("day_of_week"), rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime(),
                        rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class)), new Object[]{id});
    }

    @Override
    public List<Object[]> findLockedWorkingHour(UUID id) {
        return jdbc.query("SELECT doctor_id,day_of_week,effective_from,effective_until FROM doctor_working_hours WHERE id=? FOR UPDATE", (rs, n) -> new Object[]{rs.getObject("doctor_id", UUID.class), rs.getInt("day_of_week"), rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class)}, new Object[]{id});
    }

    @Override
    public Boolean workingHourHasAppointments(UUID doctor, String zone1, int weekday1, String zone2, LocalDate from, LocalDate until, UUID excludedHour, int weekday2, String zone3, String zone4, String zone5) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments a " +
                        "WHERE a.doctor_id=? AND a.status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND a.scheduled_at>=now() " +
                        "AND EXTRACT(ISODOW FROM a.scheduled_at AT TIME ZONE ?)=? " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::date BETWEEN ? AND COALESCE(?::date,DATE 'infinity') " +
                        "AND NOT EXISTS(SELECT 1 FROM doctor_working_hours h WHERE h.id<>? AND h.doctor_id=a.doctor_id AND h.day_of_week=? " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::date BETWEEN h.effective_from AND COALESCE(h.effective_until,DATE 'infinity') " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::time>=h.starts_at " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::time + a.duration_minutes*INTERVAL '1 minute' <= h.ends_at))", Boolean.class, new Object[]{doctor, zone1, weekday1, zone2, from, until, excludedHour, weekday2, zone3, zone4, zone5});
    }

    @Override
    public int deleteWorkingHour(UUID id) {
        return jdbc.update("DELETE FROM doctor_working_hours WHERE id=?", new Object[]{id});
    }

    @Override
    public int insertLeave(UUID id, UUID doctor, OffsetDateTime from, OffsetDateTime until, String reason) {
        return jdbc.update("INSERT INTO doctor_leave(id,doctor_id,starts_at,ends_at,status,reason) VALUES (?,?,?,?,'PENDING',?)", new Object[]{id, doctor, from, until, reason});
    }

    @Override
    public SchedulingAdminModels.LeaveItem findLeave(UUID id) {
        return jdbc.queryForObject("SELECT l.id,l.doctor_id,d.display_name,l.starts_at,l.ends_at,l.status,l.reason FROM doctor_leave l JOIN doctors d ON d.user_id=l.doctor_id WHERE l.id=?", (rs, n) -> new SchedulingAdminModels.LeaveItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class), rs.getString("display_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), rs.getString("reason")), new Object[]{id});
    }

    @Override
    public List<Object[]> findLockedLeave(UUID id) {
        return jdbc.query("SELECT doctor_id,starts_at,ends_at,status FROM doctor_leave WHERE id=? FOR UPDATE", (rs, n) -> new Object[]{rs.getObject("doctor_id", UUID.class), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status")}, new Object[]{id});
    }

    @Override
    public int updateLeave(String status, String reason, UUID id) {
        return jdbc.update("UPDATE doctor_leave SET status=?,reason=COALESCE(NULLIF(?,''),reason) WHERE id=?", new Object[]{status, reason, id});
    }

    @Override
    public SchedulingAdminModels.LeaveItem findReviewedLeave(UUID id) {
        return jdbc.queryForObject("SELECT l.id,l.doctor_id,d.display_name,l.starts_at,l.ends_at,l.status,l.reason FROM doctor_leave l JOIN doctors d ON d.user_id=l.doctor_id WHERE l.id=?", (rs, n) -> new SchedulingAdminModels.LeaveItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class), rs.getString("display_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), rs.getString("reason")), new Object[]{id});
    }

    @Override
    public int insertClosure(UUID id, OffsetDateTime from, OffsetDateTime until, String reason) {
        return jdbc.update("INSERT INTO clinic_closures(id,starts_at,ends_at,reason) VALUES (?,?,?,?)", new Object[]{id, from, until, reason});
    }

    @Override
    public int deleteClosure(UUID id) {
        return jdbc.update("DELETE FROM clinic_closures WHERE id=?", new Object[]{id});
    }

    @Override
    public Integer lockSettings() {
        return jdbc.queryForObject("SELECT id FROM clinic_settings WHERE id=1 FOR UPDATE", Integer.class, new Object[]{});
    }

    @Override
    public int insertAudit(UUID actor, String label, String action, UUID entity, String trace) {
        return jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id) VALUES (?,? ,?,'SCHEDULING_CONFIGURATION',?,?)", new Object[]{actor, label, action, entity, trace});
    }

    private static OffsetDateTime databaseTime(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
}
