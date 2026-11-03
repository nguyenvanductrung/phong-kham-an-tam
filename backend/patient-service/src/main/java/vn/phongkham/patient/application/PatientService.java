package vn.phongkham.patient.application;

import vn.phongkham.patient.api.PatientModels;
import java.util.UUID;

public interface PatientService {
    PatientModels.PatientPage search(String query, int page, int size, String status);
    PatientModels.PatientSummary create(PatientModels.CreatePatientRequest request, UUID actor, String trace);
    PatientModels.PatientSummary update(UUID id, PatientModels.UpdatePatientRequest request, UUID actor, String trace);
    void delete(UUID id, UUID actor, String trace, String token);
    PatientModels.PatientSummary setArchived(UUID id, boolean archived, UUID actor, String trace, String token);
    PatientModels.PatientSummary findSummary(UUID id);
    PatientModels.PatientDetail findDetail(UUID id);
    boolean isLinkedTo(UUID patientId, UUID userId);
    PatientModels.PatientSummary findLinkedPatient(UUID userId);
    PatientModels.AccountLink linkAccount(UUID patientId, UUID userId, UUID actor, String trace);
    PatientModels.AccountLink accountLink(UUID patientId);
    boolean hasDoctorGrant(UUID patientId, UUID doctorId);
}
