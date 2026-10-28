package vn.phongkham.patient.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class PatientModels {
    private PatientModels() {}
    public record CreatePatientRequest(@NotBlank @Size(max=200) String fullName,
                                       @NotBlank @Size(max=32) String phone,
                                       @Size(max=254) String email,
                                       String gender, LocalDate dateOfBirth,
                                       @Size(max=500) String address,
                                       @Size(max=500) String emergencyContact,
                                       @Size(max=32) String emergencyPhone,
                                       @Size(max=1000) String allergies,
                                       @Size(max=4000) String medicalHistory) {}
    public record PatientSummary(UUID id, String patientCode, String fullName, String phone, String email,
                                 LocalDate dateOfBirth, String gender, String address, String emergencyContact,
                                 String emergencyPhone, java.time.Instant createdAt) {}
    public record PatientDetail(UUID id, String patientCode, String fullName, String phone, String email,
                                LocalDate dateOfBirth, String gender, String address, String emergencyContact,
                                String emergencyPhone, String allergies, String medicalHistory, String notes,
                                java.time.Instant createdAt, java.time.Instant updatedAt) {}
    public record PatientPage(java.util.List<PatientSummary> items, int page, int size, boolean hasNext) {}
    public record LinkAccountRequest(@jakarta.validation.constraints.NotNull UUID userId) {}
    public record AccountLink(boolean linked,UUID userId) {}
}

