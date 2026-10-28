package vn.phongkham.scheduling.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingAdminModels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class SchedulingAdminService {
    private final JdbcTemplate jdbc;
    private final String clinicTimezone;

    public SchedulingAdminService(JdbcTemplate jdbc, @Value("${app.clinic-timezone}") String clinicTimezone) {
        this.jdbc = jdbc;
        this.clinicTimezone = clinicTimezone;
    }

    @Transactional(readOnly = true)
    public SchedulingAdminModels.CatalogSnapshot snapshot() {
        var services = jdbc.query("SELECT id,code,name,description,price,duration_minutes,is_active FROM services ORDER BY name",
                (rs, n) -> new SchedulingAdminModels.ServiceItem(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("description"), rs.getBigDecimal("price"),
                        rs.getInt("duration_minutes"), rs.getBoolean("is_active")));
        var doctors = jdbc.query("SELECT user_id,display_name,specialty,is_bookable FROM doctors ORDER BY display_name",
                (rs, n) -> new SchedulingAdminModels.DoctorItem(rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                        rs.getString("specialty"), rs.getBoolean("is_bookable")));
        var chairs = jdbc.query("SELECT id,name,room_type,is_active FROM chairs ORDER BY name",
                (rs, n) -> new SchedulingAdminModels.ChairItem(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("room_type"), rs.getBoolean("is_active")));
        var hours = jdbc.query("SELECT h.id,h.doctor_id,d.display_name,h.day_of_week,h.starts_at,h.ends_at,h.effective_from,h.effective_until " +
                        "FROM doctor_working_hours h JOIN doctors d ON d.user_id=h.doctor_id ORDER BY d.display_name,h.day_of_week,h.starts_at",
                (rs, n) -> new SchedulingAdminModels.WorkingHourItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getString("display_name"), rs.getInt("day_of_week"), rs.getTime("starts_at").toLocalTime(),
                        rs.getTime("ends_at").toLocalTime(), rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class)));
        var leaves = jdbc.query("SELECT l.id,l.doctor_id,d.display_name,l.starts_at,l.ends_at,l.status,l.reason " +
                        "FROM doctor_leave l JOIN doctors d ON d.user_id=l.doctor_id ORDER BY l.starts_at DESC LIMIT 200",
                (rs, n) -> new SchedulingAdminModels.LeaveItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getString("display_name"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                        rs.getString("status"), rs.getString("reason")));
        var closures = jdbc.query("SELECT id,starts_at,ends_at,reason FROM clinic_closures ORDER BY starts_at DESC LIMIT 200",
                (rs, n) -> new SchedulingAdminModels.ClosureItem(rs.getObject("id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(), rs.getString("reason")));
        return new SchedulingAdminModels.CatalogSnapshot(services, doctors, chairs, hours, leaves, closures);
    }

    @Transactional
    public SchedulingAdminModels.ServiceItem saveService(UUID id, SchedulingAdminModels.ServiceRequest request, UUID actor, String requestId) {
        lockSettings();
        if (request.durationMinutes() < 15 || request.durationMinutes() > 480 || request.durationMinutes() % 15 != 0)
            throw badRequest("INVALID_DURATION", "Thời lượng dịch vụ phải là bội số 15 phút và không quá 8 tiếng.");
        boolean duplicate = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM services WHERE code=? AND (?::uuid IS NULL OR id<>?))",
                Boolean.class, request.code(), id, id));
        if (duplicate) throw conflict("SERVICE_CODE_EXISTS", "Mã dịch vụ đã được sử dụng.");
        UUID serviceId = id == null ? UUID.randomUUID() : id;
        String description = blank(request.description());
        if (id == null) {
            jdbc.update("INSERT INTO services(id,code,name,description,price,duration_minutes,is_active) VALUES (?,?,?,?,?,?,?)",
                    serviceId, request.code(), request.name().trim(), description, request.price(), request.durationMinutes(), request.active());
        } else {
            requireExists("SELECT EXISTS(SELECT 1 FROM services WHERE id=?)", id, "SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
            jdbc.update("UPDATE services SET code=?,name=?,description=?,price=?,duration_minutes=?,is_active=?,updated_at=now() WHERE id=?",
                    request.code(), request.name().trim(), description, request.price(), request.durationMinutes(), request.active(), id);
        }
        audit(actor, id == null ? "SERVICE_CREATED" : "SERVICE_UPDATED", serviceId, requestId);
        return jdbc.queryForObject("SELECT id,code,name,description,price,duration_minutes,is_active FROM services WHERE id=?",
                (rs, n) -> new SchedulingAdminModels.ServiceItem(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"), rs.getInt("duration_minutes"), rs.getBoolean("is_active")), serviceId);
    }

    @Transactional
    public SchedulingAdminModels.ChairItem saveChair(UUID id, SchedulingAdminModels.ChairRequest request, UUID actor, String requestId) {
        lockSettings();
        boolean duplicate = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chairs WHERE lower(name)=lower(?) AND (?::uuid IS NULL OR id<>?))",
                Boolean.class, request.name().trim(), id, id));
        if (duplicate) throw conflict("CHAIR_NAME_EXISTS", "Tên ghế đã được sử dụng.");
        UUID chairId = id == null ? UUID.randomUUID() : id;
        if (!request.active() && hasFutureAppointment("chair_id", id == null ? chairId : id))
            throw conflict("CHAIR_HAS_FUTURE_APPOINTMENTS", "Không thể tắt ghế đang có lịch hẹn sắp tới.");
        if (id == null) {
            jdbc.update("INSERT INTO chairs(id,name,room_type,is_active) VALUES (?,?,?,?)", chairId, request.name().trim(), request.roomType(), request.active());
        } else {
            requireExists("SELECT EXISTS(SELECT 1 FROM chairs WHERE id=?)", id, "CHAIR_NOT_FOUND", "Không tìm thấy ghế.");
            jdbc.update("UPDATE chairs SET name=?,room_type=?,is_active=? WHERE id=?", request.name().trim(), request.roomType(), request.active(), id);
        }
        audit(actor, id == null ? "CHAIR_CREATED" : "CHAIR_UPDATED", chairId, requestId);
        return new SchedulingAdminModels.ChairItem(chairId, request.name().trim(), request.roomType(), request.active());
    }

    @Transactional
    public SchedulingAdminModels.WorkingHourItem addWorkingHour(SchedulingAdminModels.WorkingHourRequest request, UUID actor, String requestId) {
        lockSettings();
        requireExists("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=?)", request.doctorId(), "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
        validateDateRange(request.effectiveFrom(), request.effectiveUntil());
        validateWorkingInterval(request.startsAt(), request.endsAt());
        boolean overlaps = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctor_working_hours " +
                "WHERE doctor_id=? AND day_of_week=? AND starts_at<? AND ends_at>? " +
                "AND daterange(effective_from,COALESCE(effective_until,DATE 'infinity'),'[]') && daterange(?,COALESCE(?::date,DATE 'infinity'),'[]'))",
                Boolean.class, request.doctorId(), request.dayOfWeek(), request.endsAt(), request.startsAt(), request.effectiveFrom(), request.effectiveUntil()));
        if (overlaps) throw conflict("WORKING_HOURS_OVERLAP", "Ca làm việc bị trùng với một cấu hình đã có hiệu lực trong cùng khoảng ngày.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO doctor_working_hours(id,doctor_id,day_of_week,starts_at,ends_at,effective_from,effective_until) VALUES (?,?,?,?,?,?,?)",
                id, request.doctorId(), request.dayOfWeek(), request.startsAt(), request.endsAt(), request.effectiveFrom(), request.effectiveUntil());
        audit(actor, "WORKING_HOUR_CREATED", id, requestId);
        return jdbc.queryForObject("SELECT h.id,h.doctor_id,d.display_name,h.day_of_week,h.starts_at,h.ends_at,h.effective_from,h.effective_until " +
                        "FROM doctor_working_hours h JOIN doctors d ON d.user_id=h.doctor_id WHERE h.id=?",
                (rs, n) -> new SchedulingAdminModels.WorkingHourItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getString("display_name"), rs.getInt("day_of_week"), rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime(),
                        rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class)), id);
    }

    @Transactional
    public void deleteWorkingHour(UUID id, UUID actor, String requestId) {
        lockSettings();
        var row = jdbc.query("SELECT doctor_id,day_of_week,effective_from,effective_until FROM doctor_working_hours WHERE id=? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getObject("doctor_id", UUID.class), rs.getInt("day_of_week"), rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_until", LocalDate.class)}, id)
                .stream().findFirst().orElseThrow(() -> notFound("WORKING_HOUR_NOT_FOUND", "Không tìm thấy ca làm việc."));
        UUID doctorId = (UUID) row[0]; int day = (int) row[1]; LocalDate from = (LocalDate) row[2]; LocalDate until = (LocalDate) row[3];
        boolean invalidates = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments a " +
                        "WHERE a.doctor_id=? AND a.status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND a.scheduled_at>=now() " +
                        "AND EXTRACT(ISODOW FROM a.scheduled_at AT TIME ZONE ?)=? " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::date BETWEEN ? AND COALESCE(?::date,DATE 'infinity') " +
                        "AND NOT EXISTS(SELECT 1 FROM doctor_working_hours h WHERE h.id<>? AND h.doctor_id=a.doctor_id AND h.day_of_week=? " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::date BETWEEN h.effective_from AND COALESCE(h.effective_until,DATE 'infinity') " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::time>=h.starts_at " +
                        "AND (a.scheduled_at AT TIME ZONE ?)::time + a.duration_minutes*INTERVAL '1 minute' <= h.ends_at))",
                Boolean.class, doctorId, clinicTimezone, day, clinicTimezone, from, until, id, day, clinicTimezone, clinicTimezone, clinicTimezone));
        if (invalidates) throw conflict("WORKING_HOUR_HAS_APPOINTMENTS", "Không thể xóa ca làm đang bao phủ lịch hẹn trong tương lai.");
        jdbc.update("DELETE FROM doctor_working_hours WHERE id=?", id);
        audit(actor, "WORKING_HOUR_DELETED", id, requestId);
    }

    @Transactional
    public SchedulingAdminModels.LeaveItem requestLeave(SchedulingAdminModels.LeaveRequest request, UUID actor, String requestId) {
        lockSettings();
        if (!request.endsAt().isAfter(request.startsAt())) throw badRequest("INVALID_TIME_RANGE", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        requireExists("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=?)", request.doctorId(), "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO doctor_leave(id,doctor_id,starts_at,ends_at,status,reason) VALUES (?,?,?,?,'PENDING',?)",
                id, request.doctorId(), databaseTime(request.startsAt()), databaseTime(request.endsAt()), blank(request.reason()));
        audit(actor, "DOCTOR_LEAVE_REQUESTED", id, requestId);
        return jdbc.queryForObject("SELECT l.id,l.doctor_id,d.display_name,l.starts_at,l.ends_at,l.status,l.reason FROM doctor_leave l JOIN doctors d ON d.user_id=l.doctor_id WHERE l.id=?",
                (rs, n) -> new SchedulingAdminModels.LeaveItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class), rs.getString("display_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), rs.getString("reason")), id);
    }

    @Transactional
    public SchedulingAdminModels.LeaveItem reviewLeave(UUID id, SchedulingAdminModels.LeaveReviewRequest request, UUID actor, String requestId) {
        lockSettings();
        var leave = jdbc.query("SELECT doctor_id,starts_at,ends_at,status FROM doctor_leave WHERE id=? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getObject("doctor_id", UUID.class), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status")}, id)
                .stream().findFirst().orElseThrow(() -> notFound("DOCTOR_LEAVE_NOT_FOUND", "Không tìm thấy yêu cầu nghỉ."));
        if (!"PENDING".equals(leave[3])) throw conflict("DOCTOR_LEAVE_ALREADY_REVIEWED", "Yêu cầu nghỉ đã được xử lý.");
        UUID doctorId = (UUID) leave[0]; Instant startsAt = (Instant) leave[1]; Instant endsAt = (Instant) leave[2];
        String status = request.decision() == SchedulingAdminModels.LeaveReviewRequest.LeaveDecision.APPROVE ? "APPROVED" : "REJECTED";
        if ("APPROVED".equals(status) && hasAppointmentOverlap(doctorId, null, startsAt, endsAt))
            throw conflict("DOCTOR_LEAVE_CONFLICTS_WITH_APPOINTMENTS", "Không thể duyệt nghỉ vì bác sĩ đã có lịch hẹn trong khoảng thời gian này.");
        jdbc.update("UPDATE doctor_leave SET status=?,reason=COALESCE(NULLIF(?,''),reason) WHERE id=?", status, blank(request.reason()), id);
        audit(actor, "APPROVED".equals(status) ? "DOCTOR_LEAVE_APPROVED" : "DOCTOR_LEAVE_REJECTED", id, requestId);
        return jdbc.queryForObject("SELECT l.id,l.doctor_id,d.display_name,l.starts_at,l.ends_at,l.status,l.reason FROM doctor_leave l JOIN doctors d ON d.user_id=l.doctor_id WHERE l.id=?",
                (rs, n) -> new SchedulingAdminModels.LeaveItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class), rs.getString("display_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), rs.getString("reason")), id);
    }

    @Transactional
    public SchedulingAdminModels.ClosureItem addClosure(SchedulingAdminModels.ClosureRequest request, UUID actor, String requestId) {
        lockSettings();
        if (!request.endsAt().isAfter(request.startsAt())) throw badRequest("INVALID_TIME_RANGE", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        if (hasAppointmentOverlap(null, null, request.startsAt(), request.endsAt()))
            throw conflict("CLOSURE_CONFLICTS_WITH_APPOINTMENTS", "Không thể đóng cửa khi đang có lịch hẹn trong khoảng thời gian này.");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_closures(id,starts_at,ends_at,reason) VALUES (?,?,?,?)", id, databaseTime(request.startsAt()), databaseTime(request.endsAt()), request.reason().trim());
        audit(actor, "CLINIC_CLOSURE_CREATED", id, requestId);
        return new SchedulingAdminModels.ClosureItem(id, request.startsAt(), request.endsAt(), request.reason().trim());
    }

    @Transactional
    public void deleteClosure(UUID id, UUID actor, String requestId) {
        lockSettings();
        int deleted = jdbc.update("DELETE FROM clinic_closures WHERE id=?", id);
        if (deleted == 0) throw notFound("CLINIC_CLOSURE_NOT_FOUND", "Không tìm thấy lịch đóng cửa.");
        audit(actor, "CLINIC_CLOSURE_DELETED", id, requestId);
    }

    private void lockSettings() { jdbc.queryForObject("SELECT id FROM clinic_settings WHERE id=1 FOR UPDATE", Integer.class); }
    private boolean hasFutureAppointment(String column, UUID value) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments WHERE " + column + "=? AND status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND scheduled_at>=now())", Boolean.class, value));
    }
    private boolean hasAppointmentOverlap(UUID doctorId, UUID chairId, Instant startsAt, Instant endsAt) {
        if (doctorId != null && chairId != null) throw new IllegalArgumentException("Only one resource filter is supported");
        String resourceCondition = doctorId != null ? "doctor_id=? AND " : chairId != null ? "chair_id=? AND " : "";
        Object[] args = doctorId != null ? new Object[]{doctorId, databaseTime(startsAt), databaseTime(endsAt)} : chairId != null ? new Object[]{chairId, databaseTime(startsAt), databaseTime(endsAt)} : new Object[]{databaseTime(startsAt), databaseTime(endsAt)};
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments WHERE " + resourceCondition +
                "status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND scheduled_at + duration_minutes*INTERVAL '1 minute' > now() " +
                "AND during && tstzrange(?,?,'[)'))", Boolean.class, args));
    }
    private void requireExists(String sql, Object id, String code, String message) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, id))) throw notFound(code, message);
    }
    private void audit(UUID actor, String action, UUID entity, String requestId) {
        jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id) VALUES (?,? ,?,'SCHEDULING_CONFIGURATION',?,?)",
                actor, "SCHEDULING_ADMIN", action, entity, requestId);
    }
    private static void validateDateRange(LocalDate from, LocalDate until) {
        if (until != null && until.isBefore(from)) throw badRequest("INVALID_DATE_RANGE", "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
    }
    private static void validateWorkingInterval(LocalTime startsAt, LocalTime endsAt) {
        if (!endsAt.isAfter(startsAt) || startsAt.getSecond() != 0 || startsAt.getNano() != 0 || endsAt.getSecond() != 0 || endsAt.getNano() != 0 || startsAt.getMinute() % 15 != 0 || endsAt.getMinute() % 15 != 0)
            throw badRequest("INVALID_WORKING_HOURS", "Ca làm phải tăng dần và nằm trên mốc 15 phút.");
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static OffsetDateTime databaseTime(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
