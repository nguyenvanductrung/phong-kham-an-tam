package vn.phongkham.scheduling.application;

import java.util.UUID;

public interface PatientLifecycleService {
    void change(UUID patientId,UUID operation,String state);
    void compensate(UUID patientId,UUID operation,String state);
    void requireActive(UUID patientId);
}
