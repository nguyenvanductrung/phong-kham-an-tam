package vn.phongkham.scheduling.application;

import vn.phongkham.scheduling.api.SchedulingModels;
import java.util.Set;
import java.util.UUID;

/** Application use cases consumed by controllers and other services. */
public interface AppointmentService {
    SchedulingModels.AppointmentResponse book(SchedulingModels.CreateAppointmentRequest request,String key,UUID actor,Set<String> roles,String correlationId,String accessToken);
    SchedulingModels.AppointmentResponse command(UUID id,SchedulingModels.AppointmentCommand command,String key,UUID actor,Set<String> roles,String correlationId,String accessToken);
    SchedulingModels.AppointmentResponse reschedule(UUID id,SchedulingModels.RescheduleRequest request,String key,UUID actor,Set<String> roles,String correlationId,String accessToken);

}
