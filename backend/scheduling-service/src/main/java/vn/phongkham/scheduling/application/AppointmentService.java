package vn.phongkham.scheduling.application;

import org.springframework.stereotype.Service;
import vn.phongkham.scheduling.api.SchedulingModels;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import vn.phongkham.common.ApiException;

@Service
public class AppointmentService {
    private final AppointmentWriter writer;
    private final PatientLookupClient patientLookup;
    public AppointmentService(AppointmentWriter writer, PatientLookupClient patientLookup) { this.writer=writer; this.patientLookup=patientLookup; }
    public SchedulingModels.AppointmentResponse book(SchedulingModels.CreateAppointmentRequest request,String key,UUID actor,Set<String> roles,String correlationId,String accessToken) {
        if(roles.stream().noneMatch(Set.of("PATIENT","RECEPTIONIST","CLINIC_MANAGER","SUPER_ADMIN")::contains))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền đặt lịch.");
        PatientLookupClient.PatientSummary patient=roles.contains("PATIENT")
                ? patientLookup.requireOwns(request.patientId(),accessToken)
                : patientLookup.requireExists(request.patientId(),accessToken);
        return writer.book(request,key,actor,roles,correlationId,patient);
    }
    public SchedulingModels.AppointmentResponse command(UUID id,SchedulingModels.AppointmentCommand command,String key,UUID actor,Set<String> roles,String correlationId,String accessToken) {
        if(roles.contains("PATIENT")) patientLookup.requireOwns(writer.get(id).patientId(),accessToken);
        return writer.command(id,command,key,actor,roles,correlationId);
    }
    public SchedulingModels.AppointmentResponse reschedule(UUID id,SchedulingModels.RescheduleRequest request,String key,UUID actor,Set<String> roles,String correlationId,String accessToken) {
        if(roles.contains("PATIENT")) patientLookup.requireOwns(writer.get(id).patientId(),accessToken);
        return writer.reschedule(id,request,key,actor,roles,correlationId);
    }
}

