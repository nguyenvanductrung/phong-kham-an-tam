package vn.phongkham.scheduling.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.scheduling.persistence.ClinicalSignedConsumerDao.*;

@Repository
public class JdbcClinicalSignedConsumerDao implements ClinicalSignedConsumerDao {
    private final JdbcTemplate jdbc;
    public JdbcClinicalSignedConsumerDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public int recordEvent(UUID event, UUID record, long version) {
        return jdbc.update("INSERT INTO processed_events(event_id,aggregate_id,aggregate_version) VALUES (?,?,?) ON CONFLICT DO NOTHING", new Object[]{event, record, version});
    }

    @Override
    public List<Appointment> findLockedAppointment(UUID appointment) {
        return jdbc.query("SELECT patient_id,doctor_id,status FROM appointments WHERE id=? FOR UPDATE", (rs,n)->new Appointment(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getString("status")), new Object[]{appointment});
    }

    @Override
    public int completeAppointment(UUID appointment) {
        return jdbc.update("UPDATE appointments SET status='COMPLETED',completed_at=now(),updated_at=now(),version=version+1 WHERE id=?", new Object[]{appointment});
    }
}
