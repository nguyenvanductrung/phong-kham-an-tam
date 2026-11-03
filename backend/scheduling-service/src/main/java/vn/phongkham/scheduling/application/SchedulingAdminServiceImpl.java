package vn.phongkham.scheduling.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import vn.phongkham.scheduling.persistence.SchedulingAdminDao;
import vn.phongkham.scheduling.persistence.SchedulingAdminDao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingAdminModels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SchedulingAdminServiceImpl implements SchedulingAdminService {
    private final SchedulingAdminDao dao;
    private final String clinicTimezone;

    public SchedulingAdminServiceImpl(SchedulingAdminDao dao, @Value("${app.clinic-timezone}") String clinicTimezone) {
        this.dao = dao;
        this.clinicTimezone = clinicTimezone;
    }

    @Transactional(readOnly = true)
    public SchedulingAdminModels.CatalogSnapshot snapshot() {
        var services = dao.listServices();
        var doctors = dao.listDoctors();
        var chairs = dao.listChairs();
        var hours = dao.listWorkingHours();
        var leaves = dao.listLeaves();
        var closures = dao.listClosures();
        return new SchedulingAdminModels.CatalogSnapshot(services, doctors, chairs, hours, leaves, closures);
    }

    @Transactional
    public SchedulingAdminModels.ServiceItem saveService(UUID id, SchedulingAdminModels.ServiceRequest request, UUID actor, String requestId) {
        lockSettings();
        if (request.durationMinutes() < 15 || request.durationMinutes() > 480 || request.durationMinutes() % 15 != 0)
            throw badRequest("INVALID_DURATION", "Thời lượng dịch vụ phải là bội số 15 phút và không quá 8 tiếng.");
        boolean duplicate = Boolean.TRUE.equals(dao.serviceCodeExists(request.code(), id, id));
        if (duplicate) throw conflict("SERVICE_CODE_EXISTS", "Mã dịch vụ đã được sử dụng.");
        UUID serviceId = id == null ? UUID.randomUUID() : id;
        String description = blank(request.description());
        if (id == null) {
            dao.insertService(serviceId, request.code(), request.name().trim(), description, request.price(), request.durationMinutes(), request.active());
        } else {
            requireExists(dao.serviceExists( id), "SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
            dao.updateService(request.code(), request.name().trim(), description, request.price(), request.durationMinutes(), request.active(), id);
        }
        audit(actor, id == null ? "SERVICE_CREATED" : "SERVICE_UPDATED", serviceId, requestId);
        return dao.findService(serviceId);
    }

    @Transactional
    public SchedulingAdminModels.ChairItem saveChair(UUID id, SchedulingAdminModels.ChairRequest request, UUID actor, String requestId) {
        lockSettings();
        boolean duplicate = Boolean.TRUE.equals(dao.chairNameExists(request.name().trim(), id, id));
        if (duplicate) throw conflict("CHAIR_NAME_EXISTS", "Tên ghế đã được sử dụng.");
        UUID chairId = id == null ? UUID.randomUUID() : id;
        if (!request.active() && dao.hasFutureChairAppointment(id == null ? chairId : id))
            throw conflict("CHAIR_HAS_FUTURE_APPOINTMENTS", "Không thể tắt ghế đang có lịch hẹn sắp tới.");
        if (id == null) {
            dao.insertChair(chairId, request.name().trim(), request.roomType(), request.active());
        } else {
            requireExists(dao.chairExists( id), "CHAIR_NOT_FOUND", "Không tìm thấy ghế.");
            dao.updateChair(request.name().trim(), request.roomType(), request.active(), id);
        }
        audit(actor, id == null ? "CHAIR_CREATED" : "CHAIR_UPDATED", chairId, requestId);
        return new SchedulingAdminModels.ChairItem(chairId, request.name().trim(), request.roomType(), request.active());
    }

    @Transactional
    public SchedulingAdminModels.WorkingHourItem addWorkingHour(SchedulingAdminModels.WorkingHourRequest request, UUID actor, String requestId) {
        lockSettings();
        requireExists(dao.doctorExists( request.doctorId()), "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
        validateDateRange(request.effectiveFrom(), request.effectiveUntil());
        validateWorkingInterval(request.startsAt(), request.endsAt());
        boolean overlaps = Boolean.TRUE.equals(dao.workingHoursOverlap(request.doctorId(), request.dayOfWeek(), request.endsAt(), request.startsAt(), request.effectiveFrom(), request.effectiveUntil()));
        if (overlaps) throw conflict("WORKING_HOURS_OVERLAP", "Ca làm việc bị trùng với một cấu hình đã có hiệu lực trong cùng khoảng ngày.");
        UUID id = UUID.randomUUID();
        dao.insertWorkingHour(id, request.doctorId(), request.dayOfWeek(), request.startsAt(), request.endsAt(), request.effectiveFrom(), request.effectiveUntil());
        audit(actor, "WORKING_HOUR_CREATED", id, requestId);
        return dao.findWorkingHour(id);
    }

    @Transactional
    public void deleteWorkingHour(UUID id, UUID actor, String requestId) {
        lockSettings();
        var row = dao.findLockedWorkingHour(id)
                .stream().findFirst().orElseThrow(() -> notFound("WORKING_HOUR_NOT_FOUND", "Không tìm thấy ca làm việc."));
        UUID doctorId = (UUID) row[0]; int day = (int) row[1]; LocalDate from = (LocalDate) row[2]; LocalDate until = (LocalDate) row[3];
        boolean invalidates = Boolean.TRUE.equals(dao.workingHourHasAppointments(doctorId, clinicTimezone, day, clinicTimezone, from, until, id, day, clinicTimezone, clinicTimezone, clinicTimezone));
        if (invalidates) throw conflict("WORKING_HOUR_HAS_APPOINTMENTS", "Không thể xóa ca làm đang bao phủ lịch hẹn trong tương lai.");
        dao.deleteWorkingHour(id);
        audit(actor, "WORKING_HOUR_DELETED", id, requestId);
    }

    @Transactional
    public SchedulingAdminModels.LeaveItem requestLeave(SchedulingAdminModels.LeaveRequest request, UUID actor, String requestId) {
        lockSettings();
        if (!request.endsAt().isAfter(request.startsAt())) throw badRequest("INVALID_TIME_RANGE", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        requireExists(dao.doctorExists( request.doctorId()), "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
        UUID id = UUID.randomUUID();
        dao.insertLeave(id, request.doctorId(), databaseTime(request.startsAt()), databaseTime(request.endsAt()), blank(request.reason()));
        audit(actor, "DOCTOR_LEAVE_REQUESTED", id, requestId);
        return dao.findLeave(id);
    }

    @Transactional
    public SchedulingAdminModels.LeaveItem reviewLeave(UUID id, SchedulingAdminModels.LeaveReviewRequest request, UUID actor, String requestId) {
        lockSettings();
        var leave = dao.findLockedLeave(id)
                .stream().findFirst().orElseThrow(() -> notFound("DOCTOR_LEAVE_NOT_FOUND", "Không tìm thấy yêu cầu nghỉ."));
        if (!"PENDING".equals(leave[3])) throw conflict("DOCTOR_LEAVE_ALREADY_REVIEWED", "Yêu cầu nghỉ đã được xử lý.");
        UUID doctorId = (UUID) leave[0]; Instant startsAt = (Instant) leave[1]; Instant endsAt = (Instant) leave[2];
        String status = request.decision() == SchedulingAdminModels.LeaveReviewRequest.LeaveDecision.APPROVE ? "APPROVED" : "REJECTED";
        if ("APPROVED".equals(status) && dao.hasAppointmentOverlap(doctorId, null, startsAt, endsAt))
            throw conflict("DOCTOR_LEAVE_CONFLICTS_WITH_APPOINTMENTS", "Không thể duyệt nghỉ vì bác sĩ đã có lịch hẹn trong khoảng thời gian này.");
        dao.updateLeave(status, blank(request.reason()), id);
        audit(actor, "APPROVED".equals(status) ? "DOCTOR_LEAVE_APPROVED" : "DOCTOR_LEAVE_REJECTED", id, requestId);
        return dao.findReviewedLeave(id);
    }

    @Transactional
    public SchedulingAdminModels.ClosureItem addClosure(SchedulingAdminModels.ClosureRequest request, UUID actor, String requestId) {
        lockSettings();
        if (!request.endsAt().isAfter(request.startsAt())) throw badRequest("INVALID_TIME_RANGE", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        if (dao.hasAppointmentOverlap(null, null, request.startsAt(), request.endsAt()))
            throw conflict("CLOSURE_CONFLICTS_WITH_APPOINTMENTS", "Không thể đóng cửa khi đang có lịch hẹn trong khoảng thời gian này.");
        UUID id = UUID.randomUUID();
        dao.insertClosure(id, databaseTime(request.startsAt()), databaseTime(request.endsAt()), request.reason().trim());
        audit(actor, "CLINIC_CLOSURE_CREATED", id, requestId);
        return new SchedulingAdminModels.ClosureItem(id, request.startsAt(), request.endsAt(), request.reason().trim());
    }

    @Transactional
    public void deleteClosure(UUID id, UUID actor, String requestId) {
        lockSettings();
        int deleted = dao.deleteClosure(id);
        if (deleted == 0) throw notFound("CLINIC_CLOSURE_NOT_FOUND", "Không tìm thấy lịch đóng cửa.");
        audit(actor, "CLINIC_CLOSURE_DELETED", id, requestId);
    }

    private void lockSettings() { dao.lockSettings(); }



    private void audit(UUID actor, String action, UUID entity, String requestId) {
        dao.insertAudit(actor, "SCHEDULING_ADMIN", action, entity, requestId);
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
    private void requireExists(boolean exists, String code, String message) { if (!exists) throw notFound(code,message); }
}
