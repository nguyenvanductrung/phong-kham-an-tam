package vn.phongkham.patient.persistence;

import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcPatientEventConsumerDao. */
public interface PatientEventConsumerDao {
    int recordEvent(UUID event, UUID aggregate, long version);
    int revokeGrant(UUID patient, UUID doctor, UUID appointment, long version);
    int upsertGrant(UUID patient, UUID doctor, UUID appointment, OffsetDateTime expiresAt, long version);

}
