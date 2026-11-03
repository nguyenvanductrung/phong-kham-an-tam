package vn.phongkham.scheduling.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import vn.phongkham.scheduling.persistence.AppointmentDao;
import vn.phongkham.scheduling.persistence.AppointmentDao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingModels;
import vn.phongkham.scheduling.domain.AppointmentStateMachine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AppointmentWriterImpl implements AppointmentWriter {
    private static final Set<String> ACTIVE = Set.of("BOOKED","CONFIRMED","ARRIVED","IN_PROGRESS");
    private final AppointmentDao dao;
    private final JsonMapper json;
    private final AppointmentStateMachine stateMachine;
    private final ZoneId clinicZone;
    private final PatientLifecycleService lifecycle;
    public AppointmentWriterImpl(AppointmentDao dao, JsonMapper json, AppointmentStateMachine stateMachine,
                             @Value("${app.clinic-timezone}") String clinicTimezone, PatientLifecycleService lifecycle) {
        this.dao=dao; this.json=json; this.stateMachine=stateMachine; this.clinicZone=ZoneId.of(clinicTimezone);
        this.lifecycle=lifecycle;
    }

    @Transactional
    public SchedulingModels.AppointmentResponse book(SchedulingModels.CreateAppointmentRequest request, String idempotencyKey,
                                                       UUID actorId, Set<String> roles, String correlationId,
                                                       PatientLookupClient.PatientSummary patient) {
        lockClinicSettings();
        byte[] requestHash=hash(request.toString());
        SchedulingModels.AppointmentResponse replay=findReplay(actorId,idempotencyKey,requestHash);
        if(replay!=null) return replay;
        lockDoctorAndPatient(request.doctorId(),request.patientId());
        lifecycle.requireActive(request.patientId());
        lockChairs(request.chairId());
        validateDuration(request.durationMinutes());
        validateDoctor(request.doctorId());
        validateChair(request.chairId());
        validateSchedule(request.doctorId(), request.scheduledAt(), request.durationMinutes());
        validateNoClosureOrLeave(request.doctorId(), request.scheduledAt(), request.durationMinutes());
        UUID serviceId=request.serviceId();
        ServiceSnapshot service=dao.findActiveService(serviceId)
                .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SERVICE_NOT_AVAILABLE","Dịch vụ đã ngừng hoạt động hoặc không tồn tại."));
        if(request.durationMinutes()!=service.duration()) throw new ApiException(HttpStatus.BAD_REQUEST,"SERVICE_DURATION_MISMATCH","Thời lượng lịch phải khớp thời lượng dịch vụ đã chọn.");
        int reserved=dao.reserveRequest(actorId, idempotencyKey, requestHash);
        if(reserved==0) {
            replay=findReplay(actorId,idempotencyKey,requestHash);
            if(replay!=null) return replay;
            throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_REQUEST_IN_PROGRESS","Yêu cầu đang được xử lý. Vui lòng thử lại.");
        }
        UUID appointmentId=UUID.randomUUID();
        dao.insertAppointment(appointmentId, "AP-"+appointmentId.toString().substring(0,8).toUpperCase(), request.patientId(), request.doctorId(), request.chairId(), databaseTime(request.scheduledAt()), request.durationMinutes(), blank(request.reason()), blank(request.patientNote()), actorId, roles.contains("PATIENT")?"PATIENT":"STAFF");
        dao.insertServiceSnapshot(appointmentId, service.id(), service.name(), service.price());
        dao.attachRequest(appointmentId, actorId, idempotencyKey);
        audit(actorId,roles,"APPOINTMENT_BOOKED",appointmentId,correlationId);
        outbox("AppointmentBooked.v1",appointmentId,1,correlationId,Map.of("appointmentId",appointmentId,"patientId",request.patientId(),
                "doctorId",request.doctorId(),"chairId",request.chairId(),"scheduledAt",request.scheduledAt(),
                "durationMinutes",request.durationMinutes(),"status","BOOKED","patientEmail",Objects.toString(patient.email(),"")));
        return find(appointmentId);
    }

    @Transactional
    public SchedulingModels.AppointmentResponse command(UUID appointmentId, SchedulingModels.AppointmentCommand command,
                                                         String idempotencyKey, UUID actorId, Set<String> roles,
                                                         String correlationId) {
        lockClinicSettings();
        var row=dao.findLockedAppointment(appointmentId)
                .stream().findFirst().orElseThrow(this::notFound);
        byte[] requestHash=hash(appointmentId+":"+command.toString());
        SchedulingModels.AppointmentResponse replay=findReplay(actorId,idempotencyKey,requestHash);
        if(replay!=null) return replay;
        if(command.type()==SchedulingModels.AppointmentCommand.Command.CANCEL) {
            if(roles.contains("PATIENT") && Instant.now().isAfter(row.scheduledAt().minus(2,ChronoUnit.HOURS)))
                throw new ApiException(HttpStatus.CONFLICT,"CANCELLATION_WINDOW_CLOSED","Bệnh nhân chỉ có thể hủy lịch trước giờ hẹn ít nhất 2 tiếng.");
            if(!roles.contains("PATIENT") && blank(command.reason())==null)
                throw new ApiException(HttpStatus.BAD_REQUEST,"CANCELLATION_REASON_REQUIRED","Lễ tân hoặc quản lý cần nhập lý do hủy.");
        }
        int grace=dao.getNoShowGrace();
        String next=stateMachine.transition(row.status(),command.type(),roles,row.scheduledAt(),grace,Instant.now());
        if(command.type()==SchedulingModels.AppointmentCommand.Command.START && roles.contains("DOCTOR") && !row.doctorId().equals(actorId))
            throw new ApiException(HttpStatus.FORBIDDEN,"DOCTOR_NOT_ASSIGNED","Chỉ bác sĩ được phân công mới được bắt đầu lượt khám.");
        if(command.type()==SchedulingModels.AppointmentCommand.Command.CANCEL) {
            dao.cancelAppointment(next, blank(command.reason()), appointmentId, row.version());
        } else if(command.type()==SchedulingModels.AppointmentCommand.Command.CHECK_IN) {
            dao.checkInAppointment(next, appointmentId, row.version());
        } else if(command.type()==SchedulingModels.AppointmentCommand.Command.START) {
            dao.startAppointment(next, appointmentId, row.version());
        } else {
            dao.changeStatus(next, appointmentId, row.version());
        }
        dao.insertRequest(actorId, idempotencyKey, requestHash, appointmentId);
        String eventType=switch(command.type()) {
            case CONFIRM -> "AppointmentConfirmed.v1";
            case CHECK_IN -> "PatientCheckedIn.v1";
            case CANCEL -> "AppointmentCancelled.v1";
            case START -> "AppointmentStarted.v1";
            case MARK_NO_SHOW -> "AppointmentNoShow.v1";
        };
        audit(actorId,roles,eventType,appointmentId,correlationId);
        if(eventType.equals("PatientCheckedIn.v1") || eventType.equals("AppointmentCancelled.v1") || eventType.equals("AppointmentNoShow.v1"))
            outbox(eventType,appointmentId,row.version()+1,correlationId,Map.of("appointmentId",appointmentId,"patientId",row.patientId(),"doctorId",row.doctorId(),"status",next));
        return find(appointmentId);
    }

    @Transactional
    public SchedulingModels.AppointmentResponse reschedule(UUID appointmentId, SchedulingModels.RescheduleRequest request,
                                                            String idempotencyKey, UUID actorId, Set<String> roles, String correlationId) {
        if(!Collections.disjoint(roles,Set.of("RECEPTIONIST","CLINIC_MANAGER","SUPER_ADMIN","PATIENT"))) {
            // authorized roles; patient ownership is checked at the API boundary
        } else throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền đổi lịch.");
        lockClinicSettings();
        byte[] requestHash=hash(appointmentId+":"+request.toString());
        SchedulingModels.AppointmentResponse replay=findReplay(actorId,idempotencyKey,requestHash);
        if(replay!=null) return replay;
        var initial=dao.findResources(appointmentId).stream().findFirst().orElseThrow(this::notFound);
        lockDoctorAndPatient(initial.doctorId(),initial.patientId());
        lifecycle.requireActive(initial.patientId());
        replay=findReplay(actorId,idempotencyKey,requestHash);
        if(replay!=null) return replay;
        var row=dao.findLockedReschedule(appointmentId).stream().findFirst().orElseThrow(this::notFound);
        lockChairs(row.chairId(),request.chairId());
        if(!Set.of("BOOKED","CONFIRMED").contains(row.status())) throw new ApiException(HttpStatus.CONFLICT,"APPOINTMENT_NOT_RESCHEDULABLE","Chỉ lịch chưa tiếp đón mới được đổi.");
        validateDuration(request.durationMinutes()); validateChair(request.chairId());
        validateSchedule(row.doctorId(),request.scheduledAt(),request.durationMinutes());
        validateNoClosureOrLeave(row.doctorId(),request.scheduledAt(),request.durationMinutes());
        dao.rescheduleAppointment(request.chairId(), databaseTime(request.scheduledAt()), request.durationMinutes(), appointmentId, row.version());
        dao.insertRescheduleRequest(actorId, idempotencyKey, requestHash, appointmentId);
        audit(actorId,roles,"APPOINTMENT_RESCHEDULED",appointmentId,correlationId);
        outbox("AppointmentRescheduled.v1",appointmentId,row.version()+1,correlationId,Map.of("appointmentId",appointmentId,"patientId",row.patientId(),"doctorId",row.doctorId(),"scheduledAt",request.scheduledAt(),"durationMinutes",request.durationMinutes(),"status",row.status()));
        return find(appointmentId);
    }

    @Transactional(readOnly=true)
    public List<SchedulingModels.AppointmentResponse> list(Instant from, Instant to, UUID doctorId, UUID patientId, UUID actorId, Set<String> roles) {
        if(to.isBefore(from) || Duration.between(from,to).toDays()>31) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","Khoảng thời gian tối đa là 31 ngày.");
        if(roles.contains("DOCTOR")) doctorId=actorId;
        if(roles.contains("PATIENT") && patientId==null) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Không tìm thấy hồ sơ bệnh nhân liên kết.");

        return dao.listAppointments(databaseTime(from), databaseTime(to), doctorId, doctorId, patientId, patientId);
    }

    @Transactional(readOnly=true)
    public List<SchedulingModels.AppointmentResponse> history(UUID patientId, UUID actorId, Set<String> roles, int requestedLimit) {
        if (!roles.stream().anyMatch(Set.of("PATIENT", "DOCTOR", "RECEPTIONIST", "ASSISTANT", "CLINIC_MANAGER", "SUPER_ADMIN")::contains))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem lịch hẹn của hồ sơ này.");
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        UUID doctorFilter = roles.contains("DOCTOR") ? actorId : null;

        return dao.listHistory(patientId, doctorFilter, doctorFilter, limit);
    }

    @Transactional(readOnly=true)
    public SchedulingModels.AppointmentResponse get(UUID id) { return find(id); }

    @Transactional(readOnly=true)
    public SchedulingModels.AppointmentResponse getForActor(UUID id, UUID actorId, Set<String> roles) {
        SchedulingModels.AppointmentResponse appointment=find(id);
        if(roles.contains("DOCTOR") && !appointment.doctorId().equals(actorId))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền xem lịch này.");
        if(!roles.stream().anyMatch(Set.of("PATIENT","DOCTOR","RECEPTIONIST","ASSISTANT","CLINIC_MANAGER","SUPER_ADMIN")::contains))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền xem lịch này.");
        return appointment;
    }

    public List<SchedulingModels.ServiceItem> services() {
        return dao.listActiveServices();
    }
    public List<SchedulingModels.DoctorItem> doctors() {
        return dao.listBookableDoctors();
    }
    public List<SchedulingModels.ChairItem> chairs() {
        return dao.listActiveChairs();
    }

    public List<SchedulingModels.Slot> availability(SchedulingModels.AvailabilityRequest req) {
        validateDuration(req.durationMinutes());
        if(!Boolean.TRUE.equals(dao.isDoctorBookable(req.doctorId()))) throw new ApiException(HttpStatus.NOT_FOUND,"DOCTOR_NOT_AVAILABLE","Bác sĩ không nhận đặt lịch.");
        var hours=dao.findAvailabilityHours(req.doctorId(), req.date().getDayOfWeek().getValue(), req.date());
        List<SchedulingModels.Slot> result=new ArrayList<>();
        List<UUID> chairs=chairs().stream().map(SchedulingModels.ChairItem::id).toList();
        for(var interval:hours) for(LocalTime slot=interval[0];!slot.plusMinutes(req.durationMinutes()).isAfter(interval[1]);slot=slot.plusMinutes(15)) {
            Instant start=ZonedDateTime.of(req.date(),slot,clinicZone).toInstant(); Instant end=start.plus(req.durationMinutes(),ChronoUnit.MINUTES);
            if(hasClosure(start,end) || hasLeave(req.doctorId(),start,end)) continue;
            List<UUID> free=chairs.stream().filter(chair->!overlaps(req.doctorId(),chair,start,end)).toList();
            if(!free.isEmpty()) result.add(new SchedulingModels.Slot(start,free));
        }
        return result;
    }

    private void lockClinicSettings() { dao.lockSettings(); }
    private void lockDoctorAndPatient(UUID doctorId, UUID patientId) {
        dao.lockResourceKeys("0:doctor:" + doctorId, "1:patient:" + patientId);
    }
    private void lockChairs(UUID... chairIds) {
        var keys=Arrays.stream(chairIds).filter(Objects::nonNull).map(id->"2:chair:"+id).distinct().sorted().toList();
        dao.lockResourceKeys(keys.toArray(String[]::new));
    }

    private void validateDuration(int minutes) { if(minutes<15||minutes>480||minutes%15!=0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DURATION","Thời lượng lịch hẹn phải là bội số 15 phút và không quá 8 tiếng."); }
    private void validateDoctor(UUID id) { if(!Boolean.TRUE.equals(dao.isDoctorAvailable(id))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"DOCTOR_NOT_AVAILABLE","Bác sĩ không nhận đặt lịch."); }
    private void validateChair(UUID id) { if(!Boolean.TRUE.equals(dao.isChairAvailable(id))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CHAIR_NOT_AVAILABLE","Ghế không hoạt động hoặc không tồn tại."); }
    private void validateSchedule(UUID doctor,Instant start,int duration) {
        ZonedDateTime local=start.atZone(clinicZone); LocalTime slot=local.toLocalTime(); LocalDate day=local.toLocalDate();
        if(slot.getSecond()!=0||slot.getNano()!=0||slot.getMinute()%15!=0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SLOT_ALIGNMENT","Giờ hẹn phải nằm trên mốc 15 phút.");
        var hours=dao.findWorkingHours(doctor, day.getDayOfWeek().getValue(), day);
        if(hours.stream().noneMatch(h->!slot.isBefore(h[0])&&!slot.plusMinutes(duration).isAfter(h[1]))) throw new ApiException(HttpStatus.CONFLICT,"OUTSIDE_WORKING_HOURS","Giờ hẹn nằm ngoài ca làm việc của bác sĩ.");
    }
    private void validateNoClosureOrLeave(UUID doctor,Instant start,int duration) {
        Instant end=start.plus(duration,ChronoUnit.MINUTES);
        if(hasClosure(start,end)) throw new ApiException(HttpStatus.CONFLICT,"CLINIC_CLOSED","Phòng khám đóng cửa trong thời gian này.");
        if(hasLeave(doctor,start,end)) throw new ApiException(HttpStatus.CONFLICT,"DOCTOR_ON_LEAVE","Bác sĩ nghỉ trong thời gian này.");
    }
    private boolean hasClosure(Instant start,Instant end) { return Boolean.TRUE.equals(dao.hasClosure(databaseTime(start), databaseTime(end))); }
    private boolean hasLeave(UUID doctor,Instant start,Instant end) { return Boolean.TRUE.equals(dao.hasLeave(doctor, databaseTime(start), databaseTime(end))); }
    private boolean overlaps(UUID doctor,UUID chair,Instant start,Instant end) { return Boolean.TRUE.equals(dao.overlaps(doctor, chair, databaseTime(start), databaseTime(end))); }

    private SchedulingModels.AppointmentResponse findReplay(UUID actor,String key,byte[] requestHash) {
        var result=dao.findReplay(actor, key);
        if(result.isEmpty()) return null;
        Replay replay=result.getFirst();
        if(!MessageDigest.isEqual(replay.hash(),requestHash)) throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key đã được dùng với nội dung khác.");
        return replay.appointmentId()==null?null:find(replay.appointmentId());
    }
    private SchedulingModels.AppointmentResponse find(UUID id) {
        return dao.findAppointment(id).stream().findFirst().orElseThrow(this::notFound);
    }

    private void audit(UUID actor,Set<String> roles,String action,UUID id,String correlationId) {
        dao.insertAudit(actor, roles.toString(), action, "Appointment", id, correlationId, "{}");
    }
    private void outbox(String type,UUID id,long version,String correlationId,Map<String,Object> payload) {
        try { dao.insertOutbox(UUID.randomUUID(), type, databaseTime(Instant.now()), id, version, correlationId, json.writeValueAsString(payload)); }
        catch(JacksonException ex) { throw new IllegalStateException("Unable to serialize event",ex); }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"APPOINTMENT_NOT_FOUND","Không tìm thấy lịch hẹn."); }
    private static String blank(String s) { return s==null||s.isBlank()?null:s.trim(); }
    private static java.time.OffsetDateTime databaseTime(Instant instant) { return instant.atOffset(java.time.ZoneOffset.UTC); }
    private static byte[] hash(String s) { try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);} }





}
