package vn.phongkham.patient.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.patient.persistence.PatientEventConsumerDao.*;

@Repository
public class JdbcPatientEventConsumerDao implements PatientEventConsumerDao {
    private final JdbcTemplate jdbc;
    public JdbcPatientEventConsumerDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public int recordEvent(UUID event, UUID aggregate, long version) {
        return jdbc.update("INSERT INTO processed_events(event_id,aggregate_id,aggregate_version) VALUES (?,?,?) ON CONFLICT DO NOTHING", new Object[]{event, aggregate, version});
    }

    @Override
    public int revokeGrant(UUID patient, UUID doctor, UUID appointment, long version) {
        return jdbc.update("""
                    INSERT INTO patient_access_grants(patient_id,doctor_id,appointment_id,expires_at,aggregate_version,revoked_at)
                    VALUES (?,?,?,now()+INTERVAL '1 second',?,now())
                    ON CONFLICT (patient_id,doctor_id,appointment_id) DO UPDATE
                    SET revoked_at=now(),aggregate_version=EXCLUDED.aggregate_version
                    WHERE patient_access_grants.aggregate_version<EXCLUDED.aggregate_version
                    """, new Object[]{patient, doctor, appointment, version});
    }

    @Override
    public int upsertGrant(UUID patient, UUID doctor, UUID appointment, OffsetDateTime expiresAt, long version) {
        return jdbc.update("""
                INSERT INTO patient_access_grants(patient_id,doctor_id,appointment_id,expires_at,aggregate_version)
                VALUES (?,?,?,?,?)
                ON CONFLICT (patient_id,doctor_id,appointment_id) DO UPDATE
                SET expires_at=EXCLUDED.expires_at,aggregate_version=EXCLUDED.aggregate_version,revoked_at=NULL
                WHERE patient_access_grants.aggregate_version<EXCLUDED.aggregate_version
                """, new Object[]{patient, doctor, appointment, expiresAt, version});
    }
}
