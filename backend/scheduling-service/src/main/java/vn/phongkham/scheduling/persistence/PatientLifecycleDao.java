package vn.phongkham.scheduling.persistence;

import java.util.UUID;

public interface PatientLifecycleDao {
    void lock(UUID patientId);
    String state(UUID patientId);
    boolean hasAppointments(UUID patientId, boolean activeOnly);
    void change(UUID patientId, UUID operation, String state);
    void compensate(UUID patientId, UUID operation, String state);
}
