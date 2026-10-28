package vn.phongkham.scheduling.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingModels;
import vn.phongkham.scheduling.domain.AppointmentStateMachine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AppointmentWriter {
    private static final Set<String> ACTIVE = Set.of("BOOKED","CONFIRMED","ARRIVED","IN_PROGRESS");
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AppointmentStateMachine stateMachine;
    private final ZoneId clinicZone;
    public AppointmentWriter(JdbcTemplate jdbc, JsonMapper json, AppointmentStateMachine stateMachine,
                             @Value("${app.clinic-timezone}") String clinicTimezone) {
        this.jdbc=jdbc; this.json=json; this.stateMachine=stateMachine; this.clinicZone=ZoneId.of(clinicTimezone);
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
        lockChairs(request.chairId());
        validateDuration(request.durationMinutes());
        validateDoctor(request.doctorId());
        validateChair(request.chairId());
        validateSchedule(request.doctorId(), request.scheduledAt(), request.durationMinutes());
        validateNoClosureOrLeave(request.doctorId(), request.scheduledAt(), request.durationMinutes());
        UUID serviceId=request.serviceId();
        ServiceSnapshot service=jdbc.query("SELECT id,name,price,duration_minutes FROM services WHERE id=? AND is_active",(rs,n)->new ServiceSnapshot(rs.getObject("id",UUID.class),rs.getString("name"),rs.getBigDecimal("price"),rs.getInt("duration_minutes")),serviceId)
                .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SERVICE_NOT_AVAILABLE","Dịch vụ đã ngừng hoạt động hoặc không tồn tại."));
        if(request.durationMinutes()!=service.duration()) throw new ApiException(HttpStatus.BAD_REQUEST,"SERVICE_DURATION_MISMATCH","Thời lượng lịch phải khớp thời lượng dịch vụ đã chọn.");
        int reserved=jdbc.update("INSERT INTO idempotency_records(actor_id,idempotency_key,request_hash) VALUES (?,?,?) ON CONFLICT DO NOTHING",actorId,idempotencyKey,requestHash);
        if(reserved==0) {
            replay=findReplay(actorId,idempotencyKey,requestHash);
            if(replay!=null) return replay;
            throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_REQUEST_IN_PROGRESS","Yêu cầu đang được xử lý. Vui lòng thử lại.");
        }
        UUID appointmentId=UUID.randomUUID();
        jdbc.update("INSERT INTO appointments(id,appointment_number,patient_id,doctor_id,chair_id,scheduled_at,duration_minutes,reason,patient_note,created_by,source) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                appointmentId, "AP-"+appointmentId.toString().substring(0,8).toUpperCase(), request.patientId(), request.doctorId(), request.chairId(),
                databaseTime(request.scheduledAt()), request.durationMinutes(), blank(request.reason()), blank(request.patientNote()), actorId,
                roles.contains("PATIENT")?"PATIENT":"STAFF");
        jdbc.update("INSERT INTO appointment_services(appointment_id,service_id,title_snapshot,estimated_unit_price) VALUES (?,?,?,?)",
                appointmentId, service.id(), service.name(), service.price());
        jdbc.update("UPDATE idempotency_records SET appointment_id=? WHERE actor_id=? AND idempotency_key=?",appointmentId,actorId,idempotencyKey);
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
        var row=jdbc.query("SELECT id,patient_id,doctor_id,scheduled_at,status,version FROM appointments WHERE id=? FOR UPDATE",
                (rs,n)->new AppointmentRow(rs.getObject("id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getTimestamp("scheduled_at").toInstant(),rs.getString("status"),rs.getLong("version")),appointmentId)
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
        int grace=jdbc.queryForObject("SELECT no_show_grace_minutes FROM clinic_settings WHERE id=1",Integer.class);
        String next=stateMachine.transition(row.status(),command.type(),roles,row.scheduledAt(),grace,Instant.now());
        if(command.type()==SchedulingModels.AppointmentCommand.Command.START && roles.contains("DOCTOR") && !row.doctorId().equals(actorId))
            throw new ApiException(HttpStatus.FORBIDDEN,"DOCTOR_NOT_ASSIGNED","Chỉ bác sĩ được phân công mới được bắt đầu lượt khám.");
        if(command.type()==SchedulingModels.AppointmentCommand.Command.CANCEL) {
            jdbc.update("UPDATE appointments SET status=?, cancellation_reason=?, cancelled_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=?",next,blank(command.reason()),appointmentId,row.version());
        } else if(command.type()==SchedulingModels.AppointmentCommand.Command.CHECK_IN) {
            jdbc.update("UPDATE appointments SET status=?,checked_in_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=?",next,appointmentId,row.version());
        } else if(command.type()==SchedulingModels.AppointmentCommand.Command.START) {
            jdbc.update("UPDATE appointments SET status=?,started_at=now(),updated_at=now(),version=version+1 WHERE id=? AND version=?",next,appointmentId,row.version());
        } else {
            jdbc.update("UPDATE appointments SET status=?,updated_at=now(),version=version+1 WHERE id=? AND version=?",next,appointmentId,row.version());
        }
        jdbc.update("INSERT INTO idempotency_records(actor_id,idempotency_key,request_hash,appointment_id) VALUES (?,?,?,?)",actorId,idempotencyKey,requestHash,appointmentId);
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
        var initial=jdbc.query("SELECT patient_id,doctor_id FROM appointments WHERE id=?",
                (rs,n)->new ResourceRow(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class)),appointmentId).stream().findFirst().orElseThrow(this::notFound);
        lockDoctorAndPatient(initial.doctorId(),initial.patientId());
        replay=findReplay(actorId,idempotencyKey,requestHash);
        if(replay!=null) return replay;
        var row=jdbc.query("SELECT patient_id,doctor_id,chair_id,status,version FROM appointments WHERE id=? FOR UPDATE",
                (rs,n)->new RescheduleRow(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getObject("chair_id",UUID.class),rs.getString("status"),rs.getLong("version")),appointmentId).stream().findFirst().orElseThrow(this::notFound);
        lockChairs(row.chairId(),request.chairId());
        if(!Set.of("BOOKED","CONFIRMED").contains(row.status())) throw new ApiException(HttpStatus.CONFLICT,"APPOINTMENT_NOT_RESCHEDULABLE","Chỉ lịch chưa tiếp đón mới được đổi.");
        validateDuration(request.durationMinutes()); validateChair(request.chairId());
        validateSchedule(row.doctorId(),request.scheduledAt(),request.durationMinutes());
        validateNoClosureOrLeave(row.doctorId(),request.scheduledAt(),request.durationMinutes());
        jdbc.update("UPDATE appointments SET chair_id=?,scheduled_at=?,duration_minutes=?,updated_at=now(),version=version+1 WHERE id=? AND version=?",
                request.chairId(),databaseTime(request.scheduledAt()),request.durationMinutes(),appointmentId,row.version());
        jdbc.update("INSERT INTO idempotency_records(actor_id,idempotency_key,request_hash,appointment_id) VALUES (?,?,?,?)",actorId,idempotencyKey,requestHash,appointmentId);
        audit(actorId,roles,"APPOINTMENT_RESCHEDULED",appointmentId,correlationId);
        outbox("AppointmentRescheduled.v1",appointmentId,row.version()+1,correlationId,Map.of("appointmentId",appointmentId,"patientId",row.patientId(),"doctorId",row.doctorId(),"scheduledAt",request.scheduledAt(),"durationMinutes",request.durationMinutes(),"status",row.status()));
        return find(appointmentId);
    }

    @Transactional(readOnly=true)
    public List<SchedulingModels.AppointmentResponse> list(Instant from, Instant to, UUID doctorId, UUID patientId, UUID actorId, Set<String> roles) {
        if(to.isBefore(from) || Duration.between(from,to).toDays()>31) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","Khoảng thời gian tối đa là 31 ngày.");
        if(roles.contains("DOCTOR")) doctorId=actorId;
        if(roles.contains("PATIENT") && patientId==null) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Không tìm thấy hồ sơ bệnh nhân liên kết.");
        String sql="""
                SELECT a.id,a.appointment_number,a.patient_id,a.doctor_id,a.chair_id,a.scheduled_at,a.duration_minutes,a.status,a.source,a.version,
                       s.title_snapshot,s.estimated_unit_price
                FROM appointments a LEFT JOIN appointment_services s ON s.appointment_id=a.id
                WHERE a.scheduled_at>=? AND a.scheduled_at<? AND (?::uuid IS NULL OR a.doctor_id=?) AND (?::uuid IS NULL OR a.patient_id=?)
                ORDER BY a.scheduled_at LIMIT 500
                """;
        return jdbc.query(sql,(rs,n)->map(rs),databaseTime(from),databaseTime(to),doctorId,doctorId,patientId,patientId);
    }

    @Transactional(readOnly=true)
    public List<SchedulingModels.AppointmentResponse> history(UUID patientId, UUID actorId, Set<String> roles, int requestedLimit) {
        if (!roles.stream().anyMatch(Set.of("PATIENT", "DOCTOR", "RECEPTIONIST", "ASSISTANT", "CLINIC_MANAGER", "SUPER_ADMIN")::contains))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem lịch hẹn của hồ sơ này.");
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        UUID doctorFilter = roles.contains("DOCTOR") ? actorId : null;
        String sql = """
                SELECT a.id,a.appointment_number,a.patient_id,a.doctor_id,a.chair_id,a.scheduled_at,a.duration_minutes,a.status,a.source,a.version,
                       s.title_snapshot,s.estimated_unit_price
                FROM appointments a LEFT JOIN appointment_services s ON s.appointment_id=a.id
                WHERE a.patient_id=? AND (?::uuid IS NULL OR a.doctor_id=?)
                ORDER BY a.scheduled_at DESC,a.id DESC LIMIT ?
                """;
        return jdbc.query(sql, (rs, n) -> map(rs), patientId, doctorFilter, doctorFilter, limit);
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
        return jdbc.query("SELECT id,code,name,price,duration_minutes FROM services WHERE is_active ORDER BY name",
                (rs,n)->new SchedulingModels.ServiceItem(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getBigDecimal("price"),rs.getInt("duration_minutes")));
    }
    public List<SchedulingModels.DoctorItem> doctors() {
        return jdbc.query("SELECT user_id,display_name,specialty FROM doctors WHERE is_bookable ORDER BY display_name",
                (rs,n)->new SchedulingModels.DoctorItem(rs.getObject("user_id",UUID.class),rs.getString("display_name"),rs.getString("specialty")));
    }
    public List<SchedulingModels.ChairItem> chairs() {
        return jdbc.query("SELECT id,name,room_type FROM chairs WHERE is_active ORDER BY name",
                (rs,n)->new SchedulingModels.ChairItem(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("room_type")));
    }

    public List<SchedulingModels.Slot> availability(SchedulingModels.AvailabilityRequest req) {
        validateDuration(req.durationMinutes());
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=? AND is_bookable)",Boolean.class,req.doctorId()))) throw new ApiException(HttpStatus.NOT_FOUND,"DOCTOR_NOT_AVAILABLE","Bác sĩ không nhận đặt lịch.");
        var hours=jdbc.query("SELECT starts_at,ends_at FROM doctor_working_hours WHERE doctor_id=? AND day_of_week=? AND ? BETWEEN effective_from AND COALESCE(effective_until,DATE '9999-12-31') ORDER BY starts_at",
                (rs,n)->new LocalTime[]{rs.getTime("starts_at").toLocalTime(),rs.getTime("ends_at").toLocalTime()},req.doctorId(),req.date().getDayOfWeek().getValue(),req.date());
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

    private void lockClinicSettings() { jdbc.queryForObject("SELECT id FROM clinic_settings WHERE id=1 FOR SHARE",Integer.class); }
    private void lockDoctorAndPatient(UUID doctorId, UUID patientId) {
        lockResourceKeys("0:doctor:" + doctorId, "1:patient:" + patientId);
    }
    private void lockChairs(UUID... chairIds) {
        var keys=Arrays.stream(chairIds).filter(Objects::nonNull).map(id->"2:chair:"+id).distinct().sorted().toList();
        lockResourceKeys(keys.toArray(String[]::new));
    }
    private void lockResourceKeys(String... keys) {
        for(String key:keys) jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(PreparedStatement statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")){
                statement.setString(1,key);
                statement.execute();
            }
            return null;
        });
    }
    private void validateDuration(int minutes) { if(minutes<15||minutes>480||minutes%15!=0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DURATION","Thời lượng lịch hẹn phải là bội số 15 phút và không quá 8 tiếng."); }
    private void validateDoctor(UUID id) { if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctors WHERE user_id=? AND is_bookable)",Boolean.class,id))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"DOCTOR_NOT_AVAILABLE","Bác sĩ không nhận đặt lịch."); }
    private void validateChair(UUID id) { if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chairs WHERE id=? AND is_active)",Boolean.class,id))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CHAIR_NOT_AVAILABLE","Ghế không hoạt động hoặc không tồn tại."); }
    private void validateSchedule(UUID doctor,Instant start,int duration) {
        ZonedDateTime local=start.atZone(clinicZone); LocalTime slot=local.toLocalTime(); LocalDate day=local.toLocalDate();
        if(slot.getSecond()!=0||slot.getNano()!=0||slot.getMinute()%15!=0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SLOT_ALIGNMENT","Giờ hẹn phải nằm trên mốc 15 phút.");
        var hours=jdbc.query("SELECT starts_at,ends_at FROM doctor_working_hours WHERE doctor_id=? AND day_of_week=? AND ? BETWEEN effective_from AND COALESCE(effective_until,DATE '9999-12-31')",
                (rs,n)->new LocalTime[]{rs.getTime("starts_at").toLocalTime(),rs.getTime("ends_at").toLocalTime()},doctor,day.getDayOfWeek().getValue(),day);
        if(hours.stream().noneMatch(h->!slot.isBefore(h[0])&&!slot.plusMinutes(duration).isAfter(h[1]))) throw new ApiException(HttpStatus.CONFLICT,"OUTSIDE_WORKING_HOURS","Giờ hẹn nằm ngoài ca làm việc của bác sĩ.");
    }
    private void validateNoClosureOrLeave(UUID doctor,Instant start,int duration) {
        Instant end=start.plus(duration,ChronoUnit.MINUTES);
        if(hasClosure(start,end)) throw new ApiException(HttpStatus.CONFLICT,"CLINIC_CLOSED","Phòng khám đóng cửa trong thời gian này.");
        if(hasLeave(doctor,start,end)) throw new ApiException(HttpStatus.CONFLICT,"DOCTOR_ON_LEAVE","Bác sĩ nghỉ trong thời gian này.");
    }
    private boolean hasClosure(Instant start,Instant end) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clinic_closures WHERE tstzrange(starts_at,ends_at,'[)') && tstzrange(?,?,'[)'))",Boolean.class,databaseTime(start),databaseTime(end))); }
    private boolean hasLeave(UUID doctor,Instant start,Instant end) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM doctor_leave WHERE doctor_id=? AND status='APPROVED' AND tstzrange(starts_at,ends_at,'[)') && tstzrange(?,?,'[)'))",Boolean.class,doctor,databaseTime(start),databaseTime(end))); }
    private boolean overlaps(UUID doctor,UUID chair,Instant start,Instant end) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM appointments WHERE status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS') AND (doctor_id=? OR chair_id=?) AND during && tstzrange(?,?,'[)'))",Boolean.class,doctor,chair,databaseTime(start),databaseTime(end))); }

    private SchedulingModels.AppointmentResponse findReplay(UUID actor,String key,byte[] requestHash) {
        var result=jdbc.query("SELECT request_hash,appointment_id FROM idempotency_records WHERE actor_id=? AND idempotency_key=? FOR UPDATE",
                (rs,n)->new Replay(rs.getBytes("request_hash"),rs.getObject("appointment_id",UUID.class)),actor,key);
        if(result.isEmpty()) return null;
        Replay replay=result.getFirst();
        if(!MessageDigest.isEqual(replay.hash(),requestHash)) throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key đã được dùng với nội dung khác.");
        return replay.appointmentId()==null?null:find(replay.appointmentId());
    }
    private SchedulingModels.AppointmentResponse find(UUID id) {
        return jdbc.query("""
                SELECT a.id,a.appointment_number,a.patient_id,a.doctor_id,a.chair_id,a.scheduled_at,a.duration_minutes,a.status,a.source,a.version,
                       s.title_snapshot,s.estimated_unit_price
                FROM appointments a LEFT JOIN appointment_services s ON s.appointment_id=a.id WHERE a.id=?
                """,(rs,n)->map(rs),id).stream().findFirst().orElseThrow(this::notFound);
    }
    private SchedulingModels.AppointmentResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SchedulingModels.AppointmentResponse(rs.getObject("id",UUID.class),rs.getString("appointment_number"),rs.getObject("patient_id",UUID.class),
                rs.getObject("doctor_id",UUID.class),rs.getObject("chair_id",UUID.class),rs.getTimestamp("scheduled_at").toInstant(),rs.getInt("duration_minutes"),
                rs.getString("status"),rs.getString("source"),rs.getString("title_snapshot"),rs.getBigDecimal("estimated_unit_price"),rs.getLong("version"));
    }
    private void audit(UUID actor,Set<String> roles,String action,UUID id,String correlationId) {
        jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?,?,?,?,?,?,?::jsonb)",actor,roles.toString(),action,"Appointment",id,correlationId,"{}");
    }
    private void outbox(String type,UUID id,long version,String correlationId,Map<String,Object> payload) {
        try { jdbc.update("INSERT INTO outbox_events(event_id,event_type,schema_version,occurred_at,aggregate_id,aggregate_version,correlation_id,payload) VALUES (?,?,1,?,?,?,?,?::jsonb)",UUID.randomUUID(),type,databaseTime(Instant.now()),id,version,correlationId,json.writeValueAsString(payload)); }
        catch(JacksonException ex) { throw new IllegalStateException("Unable to serialize event",ex); }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"APPOINTMENT_NOT_FOUND","Không tìm thấy lịch hẹn."); }
    private static String blank(String s) { return s==null||s.isBlank()?null:s.trim(); }
    private static java.time.OffsetDateTime databaseTime(Instant instant) { return instant.atOffset(java.time.ZoneOffset.UTC); }
    private static byte[] hash(String s) { try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);} }
    private record ServiceSnapshot(UUID id,String name,BigDecimal price,int duration) {}
    private record AppointmentRow(UUID id,UUID patientId,UUID doctorId,Instant scheduledAt,String status,long version) {}
    private record ResourceRow(UUID patientId,UUID doctorId) {}
    private record RescheduleRow(UUID patientId,UUID doctorId,UUID chairId,String status,long version) {}
    private record Replay(byte[] hash,UUID appointmentId) {}
}

