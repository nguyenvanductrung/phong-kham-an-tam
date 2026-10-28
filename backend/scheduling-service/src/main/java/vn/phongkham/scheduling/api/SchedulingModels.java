package vn.phongkham.scheduling.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SchedulingModels {
    private SchedulingModels() {}
    public record ServiceItem(UUID id, String code, String name, BigDecimal price, int durationMinutes) {}
    public record DoctorItem(UUID id, String name, String specialty) {}
    public record ChairItem(UUID id, String name, String roomType) {}
    public record CreateAppointmentRequest(@NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID chairId,
                                          @NotNull UUID serviceId, @NotNull Instant scheduledAt,
                                          @Min(15) @Max(480) int durationMinutes,
                                          @Size(max=1000) String reason, @Size(max=1000) String patientNote) {}
    public record AppointmentResponse(UUID id, String appointmentNumber, UUID patientId, UUID doctorId, UUID chairId,
                                      Instant scheduledAt, int durationMinutes, String status, String source,
                                      String serviceName, BigDecimal estimatedPrice, long version) {}
    public record AppointmentCommand(@NotNull Command type, @Size(max=500) String reason) {
        public enum Command { CONFIRM, CHECK_IN, START, CANCEL, MARK_NO_SHOW }
    }
    public record RescheduleRequest(@NotNull Instant scheduledAt, @Min(15) @Max(480) int durationMinutes,
                                   @NotNull UUID chairId) {}
    public record Slot(Instant startsAt, List<UUID> availableChairs) {}
    public record AvailabilityRequest(@NotNull UUID doctorId, @NotNull java.time.LocalDate date,
                                     @Min(15) @Max(480) int durationMinutes) {}
}

