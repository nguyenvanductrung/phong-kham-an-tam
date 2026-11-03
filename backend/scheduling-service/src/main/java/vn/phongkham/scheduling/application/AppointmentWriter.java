package vn.phongkham.scheduling.application;

import vn.phongkham.scheduling.api.SchedulingModels;
import java.time.*;
import java.util.*;

/** Application use cases consumed by controllers and other services. */
public interface AppointmentWriter {
    SchedulingModels.AppointmentResponse book(SchedulingModels.CreateAppointmentRequest request, String idempotencyKey,
                                                       UUID actorId, Set<String> roles, String correlationId,
                                                       PatientLookupClient.PatientSummary patient);
    SchedulingModels.AppointmentResponse command(UUID appointmentId, SchedulingModels.AppointmentCommand command,
                                                         String idempotencyKey, UUID actorId, Set<String> roles,
                                                         String correlationId);
    SchedulingModels.AppointmentResponse reschedule(UUID appointmentId, SchedulingModels.RescheduleRequest request,
                                                            String idempotencyKey, UUID actorId, Set<String> roles, String correlationId);
    List<SchedulingModels.AppointmentResponse> list(Instant from, Instant to, UUID doctorId, UUID patientId, UUID actorId, Set<String> roles);
    List<SchedulingModels.AppointmentResponse> history(UUID patientId, UUID actorId, Set<String> roles, int requestedLimit);
    SchedulingModels.AppointmentResponse get(UUID id);
    SchedulingModels.AppointmentResponse getForActor(UUID id, UUID actorId, Set<String> roles);
    List<SchedulingModels.ServiceItem> services();
    List<SchedulingModels.DoctorItem> doctors();
    List<SchedulingModels.ChairItem> chairs();
    List<SchedulingModels.Slot> availability(SchedulingModels.AvailabilityRequest req);

}
