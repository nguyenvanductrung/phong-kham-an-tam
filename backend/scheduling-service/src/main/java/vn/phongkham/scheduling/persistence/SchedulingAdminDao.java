package vn.phongkham.scheduling.persistence;

import java.time.*;
import java.math.BigDecimal;
import vn.phongkham.scheduling.api.SchedulingAdminModels;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcSchedulingAdminDao. */
public interface SchedulingAdminDao {
    boolean hasFutureChairAppointment(UUID chairId);
    boolean hasAppointmentOverlap(UUID doctorId, UUID chairId, Instant startsAt, Instant endsAt);
    boolean serviceExists(UUID id);
    boolean chairExists(UUID id);
    boolean doctorExists(UUID id);
    List<SchedulingAdminModels.ServiceItem> listServices();
    List<SchedulingAdminModels.DoctorItem> listDoctors();
    List<SchedulingAdminModels.ChairItem> listChairs();
    List<SchedulingAdminModels.WorkingHourItem> listWorkingHours();
    List<SchedulingAdminModels.LeaveItem> listLeaves();
    List<SchedulingAdminModels.ClosureItem> listClosures();
    Boolean serviceCodeExists(String code, UUID excluded, UUID id);
    int insertService(UUID id, String code, String name, String description, BigDecimal price, int duration, boolean active);
    int updateService(String code, String name, String description, BigDecimal price, int duration, boolean active, UUID id);
    SchedulingAdminModels.ServiceItem findService(UUID id);
    Boolean chairNameExists(String name, UUID excluded, UUID id);
    int insertChair(UUID id, String name, String roomType, boolean active);
    int updateChair(String name, String roomType, boolean active, UUID id);
    Boolean workingHoursOverlap(UUID doctor, int weekday, LocalTime until, LocalTime from, LocalDate effectiveFrom, LocalDate effectiveUntil);
    int insertWorkingHour(UUID id, UUID doctor, int weekday, LocalTime from, LocalTime until, LocalDate effectiveFrom, LocalDate effectiveUntil);
    SchedulingAdminModels.WorkingHourItem findWorkingHour(UUID id);
    List<Object[]> findLockedWorkingHour(UUID id);
    Boolean workingHourHasAppointments(UUID doctor, String zone1, int weekday1, String zone2, LocalDate from, LocalDate until, UUID excludedHour, int weekday2, String zone3, String zone4, String zone5);
    int deleteWorkingHour(UUID id);
    int insertLeave(UUID id, UUID doctor, OffsetDateTime from, OffsetDateTime until, String reason);
    SchedulingAdminModels.LeaveItem findLeave(UUID id);
    List<Object[]> findLockedLeave(UUID id);
    int updateLeave(String status, String reason, UUID id);
    SchedulingAdminModels.LeaveItem findReviewedLeave(UUID id);
    int insertClosure(UUID id, OffsetDateTime from, OffsetDateTime until, String reason);
    int deleteClosure(UUID id);
    Integer lockSettings();
    int insertAudit(UUID actor, String label, String action, UUID entity, String trace);

}
