package vn.phongkham.scheduling.persistence;

import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcClinicalSignedConsumerDao. */
public interface ClinicalSignedConsumerDao {
    int recordEvent(UUID event, UUID record, long version);
    List<Appointment> findLockedAppointment(UUID appointment);
    int completeAppointment(UUID appointment);
    record Appointment(UUID patientId,UUID doctorId,String status) {}
}
