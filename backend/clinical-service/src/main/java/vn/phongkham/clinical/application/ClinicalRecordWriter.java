package vn.phongkham.clinical.application;

import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;

/** Application use cases consumed by controllers and other services. */
public interface ClinicalRecordWriter {
    ClinicalModels.RecordResponse createFromAppointment(SchedulingLookupClient.Appointment appointment,UUID actor,String correlationId,String complaint);
    ClinicalModels.RecordResponse saveDraft(UUID id,ClinicalModels.SaveDraftRequest request,UUID actor,String correlationId);
    ClinicalModels.RecordResponse createAmendment(UUID originalId,String reason,UUID actor,String correlationId);
    ClinicalModels.ToothResponse addTooth(UUID id,ClinicalModels.ToothRequest request,UUID actor,String correlationId);
    ClinicalModels.ProcedureResponse addProcedure(UUID id,ClinicalModels.ProcedureRequest request,SchedulingLookupClient.ServiceItem service,
                                                          UUID actor,String correlationId);
    ClinicalModels.RecordResponse sign(UUID id,UUID actor,String correlationId,long expectedVersion);
    ClinicalModels.PrescriptionResponse issuePrescription(UUID recordId,ClinicalModels.IssuePrescriptionRequest request,UUID actor,String correlationId);
    ClinicalModels.RecordResponse get(UUID id,UUID actor,Set<String> roles);
    List<ClinicalModels.RecordResponse> history(UUID patientId,UUID doctorId,boolean signedOnly);
    ClinicalModels.RecordWorkspace workspaceByAppointment(UUID appointmentId,UUID actor);
    ClinicalModels.RecordWorkspace workspace(UUID id,UUID actor,Set<String> roles);
    List<ClinicalModels.PublishedRecord> publishedHistory(UUID patientId);
    ClinicalModels.PublishedRecord publishedRecord(UUID recordId,UUID patientId);

}
