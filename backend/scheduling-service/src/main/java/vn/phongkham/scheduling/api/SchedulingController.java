package vn.phongkham.scheduling.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.phongkham.common.ApiException;
import vn.phongkham.common.CorrelationIdFilter;
import vn.phongkham.scheduling.application.AppointmentService;
import vn.phongkham.scheduling.application.AppointmentWriter;
import vn.phongkham.scheduling.application.PatientLookupClient;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
public class SchedulingController {
    private final AppointmentService appointments;
    private final AppointmentWriter writer;
    private final PatientLookupClient patientLookup;
    public SchedulingController(AppointmentService appointments,AppointmentWriter writer,PatientLookupClient patientLookup){this.appointments=appointments;this.writer=writer;this.patientLookup=patientLookup;}
    @GetMapping("/api/v1/catalog/services") public Object services(){return writer.services();}
    @GetMapping("/api/v1/catalog/doctors") public Object doctors(){return writer.doctors();}
    @GetMapping("/api/v1/catalog/chairs") public Object chairs(){return writer.chairs();}
    @GetMapping("/api/v1/availability") public Object availability(@Valid SchedulingModels.AvailabilityRequest request){return writer.availability(request);}

    @PostMapping("/api/v1/appointments") @ResponseStatus(HttpStatus.CREATED)
    public SchedulingModels.AppointmentResponse create(@Valid @RequestBody SchedulingModels.CreateAppointmentRequest body,@RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        validateKey(key);return appointments.book(body,key,UUID.fromString(jwt.getSubject()),roles(jwt),correlationId(request),jwt.getTokenValue());
    }
    @GetMapping("/api/v1/appointments")
    public Object list(@RequestParam Instant from,@RequestParam Instant to,@RequestParam(required=false) UUID doctorId,@RequestParam(required=false) UUID patientId,
                       @AuthenticationPrincipal Jwt jwt){
        Set<String> roles=roles(jwt);
        if(roles.contains("PATIENT")) {
            UUID linkedPatient=patientLookup.findCurrentPatient(jwt.getTokenValue()).id();
            return writer.list(from,to,null,linkedPatient,UUID.fromString(jwt.getSubject()),roles);
        }
        if(roles.stream().noneMatch(Set.of("DOCTOR","RECEPTIONIST","ASSISTANT","CLINIC_MANAGER","SUPER_ADMIN")::contains)) throw forbidden();
        return writer.list(from,to,doctorId,patientId,UUID.fromString(jwt.getSubject()),roles);
    }
    @GetMapping("/api/v1/appointments/patients/{patientId}/history")
    public Object patientHistory(@PathVariable UUID patientId,@RequestParam(defaultValue="100") int limit,@AuthenticationPrincipal Jwt jwt){
        Set<String> roles=roles(jwt);
        if(roles.contains("PATIENT"))patientLookup.requireOwns(patientId,jwt.getTokenValue());
        if(roles.stream().noneMatch(Set.of("PATIENT","DOCTOR","RECEPTIONIST","ASSISTANT","CLINIC_MANAGER","SUPER_ADMIN")::contains))throw forbidden();
        return writer.history(patientId,UUID.fromString(jwt.getSubject()),roles,limit);
    }
    @GetMapping("/api/v1/appointments/{id}")
    public SchedulingModels.AppointmentResponse get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
        Set<String> roles=roles(jwt);var appointment=writer.getForActor(id,UUID.fromString(jwt.getSubject()),roles);
        if(roles.contains("PATIENT")) patientLookup.requireOwns(appointment.patientId(),jwt.getTokenValue());
        return appointment;
    }
    @PostMapping("/api/v1/appointments/{id}/commands")
    public SchedulingModels.AppointmentResponse command(@PathVariable UUID id,@Valid @RequestBody SchedulingModels.AppointmentCommand body,@RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){validateKey(key);return appointments.command(id,body,key,UUID.fromString(jwt.getSubject()),roles(jwt),correlationId(request),jwt.getTokenValue());}
    @PutMapping("/api/v1/appointments/{id}/reschedule")
    public SchedulingModels.AppointmentResponse reschedule(@PathVariable UUID id,@Valid @RequestBody SchedulingModels.RescheduleRequest body,@RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){validateKey(key);return appointments.reschedule(id,body,key,UUID.fromString(jwt.getSubject()),roles(jwt),correlationId(request),jwt.getTokenValue());}
    private static Set<String> roles(Jwt jwt){var r=jwt.getClaimAsStringList("roles");return r==null?Set.of():Set.copyOf(r);}
    private static String correlationId(HttpServletRequest request){Object value=request.getAttribute(CorrelationIdFilter.HEADER);return value==null?UUID.randomUUID().toString():value.toString();}
    private static void validateKey(String key){if(key.length()<8||key.length()>128)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Idempotency-Key phải dài từ 8 đến 128 ký tự.");}
    private static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền xem lịch.");}
}

