package vn.phongkham.patient.persistence;

import vn.phongkham.patient.api.PatientModels;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientDao {
    List<PatientModels.PatientSummary> search(String query, String status, int limit, long offset);
    Optional<PatientModels.PatientSummary> findSummary(UUID id);
    Optional<PatientModels.PatientSummary> lock(UUID id);
    Optional<PatientModels.PatientDetail> findDetail(UUID id);
    void insert(UUID id, String code, PatientModels.CreatePatientRequest request, UUID actor);
    int update(UUID id, PatientModels.UpdatePatientRequest request);
    int delete(UUID id);
    void setArchived(UUID id, boolean archived);
    boolean hasLocalReferences(UUID id);
    boolean isLinkedTo(UUID patientId, UUID userId);
    Optional<UUID> findLinkedPatient(UUID userId);
    void linkAccount(UUID patientId, UUID userId, UUID actor);
    Optional<UUID> findLinkedAccount(UUID patientId);
    boolean hasDoctorGrant(UUID patientId, UUID doctorId);
    void audit(UUID actor, String action, UUID patientId, String trace);
}
