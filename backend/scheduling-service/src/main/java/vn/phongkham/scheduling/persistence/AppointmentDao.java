package vn.phongkham.scheduling.persistence;

import java.time.*;
import java.math.BigDecimal;
import vn.phongkham.scheduling.api.SchedulingModels;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcAppointmentDao. */
public interface AppointmentDao {
    void lockResourceKeys(String... keys);
    List<ServiceSnapshot> findActiveService(UUID serviceId);
    int reserveRequest(UUID actor, String key, byte[] hash);
    int insertAppointment(UUID id, String number, UUID patient, UUID doctor, UUID chair, OffsetDateTime startsAt, int duration, String reason, String note, UUID actor, String source);
    int insertServiceSnapshot(UUID appointment, UUID service, String title, BigDecimal price);
    int attachRequest(UUID appointment, UUID actor, String key);
    List<AppointmentRow> findLockedAppointment(UUID id);
    Integer getNoShowGrace();
    int cancelAppointment(String status, String reason, UUID id, long version);
    int checkInAppointment(String status, UUID id, long version);
    int startAppointment(String status, UUID id, long version);
    int changeStatus(String status, UUID id, long version);
    int insertRequest(UUID actor, String key, byte[] hash, UUID appointment);
    List<ResourceRow> findResources(UUID id);
    List<RescheduleRow> findLockedReschedule(UUID id);
    int rescheduleAppointment(UUID chair, OffsetDateTime startsAt, int duration, UUID id, long version);
    int insertRescheduleRequest(UUID actor, String key, byte[] hash, UUID appointment);
    List<SchedulingModels.AppointmentResponse> listAppointments(OffsetDateTime from, OffsetDateTime until, UUID doctorFilter, UUID doctor, UUID patientFilter, UUID patient);
    List<SchedulingModels.AppointmentResponse> listHistory(UUID patient, UUID doctorFilter, UUID doctor, int limit);
    List<SchedulingModels.ServiceItem> listActiveServices();
    List<SchedulingModels.DoctorItem> listBookableDoctors();
    List<SchedulingModels.ChairItem> listActiveChairs();
    Boolean isDoctorBookable(UUID doctor);
    List<LocalTime[]> findAvailabilityHours(UUID doctor, int weekday, LocalDate day);
    Integer lockSettings();
    Boolean isDoctorAvailable(UUID doctor);
    Boolean isChairAvailable(UUID chair);
    List<LocalTime[]> findWorkingHours(UUID doctor, int weekday, LocalDate day);
    Boolean hasClosure(OffsetDateTime from, OffsetDateTime until);
    Boolean hasLeave(UUID doctor, OffsetDateTime from, OffsetDateTime until);
    Boolean overlaps(UUID doctor, UUID chair, OffsetDateTime from, OffsetDateTime until);
    List<Replay> findReplay(UUID actor, String key);
    List<SchedulingModels.AppointmentResponse> findAppointment(UUID id);
    int insertAudit(UUID actor, String label, String action, String entityType, UUID entity, String trace, String metadata);
    int insertOutbox(UUID eventId, String eventType, OffsetDateTime occurredAt, UUID aggregate, long version, String trace, String payload);
    record ServiceSnapshot(UUID id,String name,BigDecimal price,int duration) {}
    record AppointmentRow(UUID id,UUID patientId,UUID doctorId,Instant scheduledAt,String status,long version) {}
    record ResourceRow(UUID patientId,UUID doctorId) {}
    record RescheduleRow(UUID patientId,UUID doctorId,UUID chairId,String status,long version) {}
    record Replay(byte[] hash,UUID appointmentId) {}
}
