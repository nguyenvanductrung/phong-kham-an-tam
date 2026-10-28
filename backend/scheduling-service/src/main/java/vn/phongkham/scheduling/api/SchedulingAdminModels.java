package vn.phongkham.scheduling.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class SchedulingAdminModels {
    private SchedulingAdminModels() {}

    public record ServiceRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            @Min(15) @Max(480) int durationMinutes,
            boolean active) {}

    public record ServiceItem(UUID id, String code, String name, String description,
                              BigDecimal price, int durationMinutes, boolean active) {}

    public record ChairRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "EXAMINATION|XRAY|SURGERY|CONSULTATION") String roomType,
            boolean active) {}

    public record ChairItem(UUID id, String name, String roomType, boolean active) {}
    public record DoctorItem(UUID id, String name, String specialty, boolean bookable) {}

    public record WorkingHourRequest(
            @NotNull UUID doctorId,
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveUntil) {}

    public record WorkingHourItem(UUID id, UUID doctorId, String doctorName, int dayOfWeek,
                                  LocalTime startsAt, LocalTime endsAt,
                                  LocalDate effectiveFrom, LocalDate effectiveUntil) {}

    public record LeaveRequest(@NotNull UUID doctorId, @NotNull Instant startsAt,
                               @NotNull Instant endsAt, @Size(max = 500) String reason) {}
    public record LeaveItem(UUID id, UUID doctorId, String doctorName, Instant startsAt,
                            Instant endsAt, String status, String reason) {}
    public record LeaveReviewRequest(@NotNull LeaveDecision decision, @Size(max = 500) String reason) {
        public enum LeaveDecision { APPROVE, REJECT }
    }

    public record ClosureRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                                 @NotBlank @Size(max = 500) String reason) {}
    public record ClosureItem(UUID id, Instant startsAt, Instant endsAt, String reason) {}

    public record CatalogSnapshot(List<ServiceItem> services, List<DoctorItem> doctors,
                                  List<ChairItem> chairs, List<WorkingHourItem> workingHours,
                                  List<LeaveItem> doctorLeaves, List<ClosureItem> clinicClosures) {}
}
