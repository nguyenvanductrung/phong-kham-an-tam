package vn.phongkham.clinical.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClinicalModels {
    private ClinicalModels() {}
    public record FromAppointmentRequest(@NotNull UUID appointmentId,@Size(max=4000) String chiefComplaint) {}
    public record SaveDraftRequest(@Size(max=4000) String chiefComplaint,@Size(max=10000) String medicalHistory,
                                   @Size(max=10000) String examinationNotes,@Size(max=5000) String diagnosis,
                                   @Size(max=10000) String treatmentNotes,Instant followUpAt,@NotNull Long version) {}
    public record AmendmentRequest(@NotBlank @Size(max=1000) String reason) {}
    public record ToothRequest(@Min(11) @Max(85) int toothNumber,@NotBlank @Size(max=40) String conditionCode,
                               List<@Pattern(regexp="MESIAL|DISTAL|OCCLUSAL|INCISAL|BUCCAL|LINGUAL|PALATAL") String> surfaces,
                               @Size(max=1000) String notes) {}
    public record ProcedureRequest(@NotNull UUID serviceId,List<@Min(11) @Max(85) Integer> toothNumbers,
                                   @DecimalMin("0.01") BigDecimal quantity,@DecimalMin("0.00") BigDecimal discountAmount,
                                   @Size(max=2000) String notes) {}
    public record PrescriptionItemRequest(@NotBlank @Size(max=200) String medicationName,@NotBlank @Size(max=200) String dosage,
                                          @NotBlank @Size(max=100) String route,@NotBlank @Size(max=200) String frequency,
                                          @Min(1) @Max(365) int durationDays,@NotBlank @Size(max=100) String quantity,
                                          @Size(max=1000) String instructions) {}
    public record IssuePrescriptionRequest(@Size(max=2000) String notes,@NotEmpty List<@Valid PrescriptionItemRequest> items) {}
    public record RecordResponse(UUID id,String recordNumber,UUID patientId,UUID appointmentId,UUID doctorId,Instant visitAt,
                                 String chiefComplaint,String medicalHistory,String examinationNotes,String diagnosis,
                                 String treatmentNotes,Instant followUpAt,String status,UUID amendsRecordId,String amendmentReason,
                                 Instant signedAt,long version) {}
    public record ProcedureResponse(UUID id,UUID serviceId,String titleSnapshot,List<Integer> toothNumbers,BigDecimal quantity,
                                    BigDecimal unitPrice,BigDecimal discountAmount,BigDecimal netAmount,Instant performedAt) {}
    public record ToothResponse(UUID id,int toothNumber,String conditionCode,List<String> surfaces,String notes) {}
    public record PrescriptionResponse(UUID id,String prescriptionNumber,Instant issuedAt,String status,int itemCount) {}
    public record PrescriptionItemResponse(UUID id,String medicationName,String dosage,String route,String frequency,
                                           int durationDays,String quantity,String instructions) {}
    public record PrescriptionDetail(UUID id,String prescriptionNumber,Instant issuedAt,String status,String notes,
                                     List<PrescriptionItemResponse> items) {}
    public record RecordWorkspace(RecordResponse record,List<ToothResponse> teeth,List<ProcedureResponse> procedures,
                                  List<PrescriptionDetail> prescriptions) {}
    public record PublishedRecord(RecordResponse record,List<ToothResponse> teeth,List<ProcedureResponse> procedures,
                                  List<PrescriptionDetail> prescriptions) {}
    public record FileResponse(UUID id,UUID recordId,String fileKind,String filename,String mimeType,long byteSize,Instant createdAt) {}
    public record CreateTreatmentPlanRequest(@NotBlank @Size(max=300) String title) {}
    public record TreatmentPlanItemRequest(@NotNull UUID serviceId,@DecimalMin("0.01") BigDecimal quantity,@Size(max=2000) String notes) {}
    public record TreatmentPlanItem(UUID id,int sequence,UUID serviceId,String title,BigDecimal quantity,BigDecimal unitPrice,String notes) {}
    public record TreatmentPlan(UUID id,UUID recordId,UUID patientId,String title,String status,long version,Instant approvedAt,List<TreatmentPlanItem> items) {}
}

