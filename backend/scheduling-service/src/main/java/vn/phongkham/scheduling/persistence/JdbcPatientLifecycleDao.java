package vn.phongkham.scheduling.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class JdbcPatientLifecycleDao implements PatientLifecycleDao {
    private final JdbcTemplate jdbc;
    public JdbcPatientLifecycleDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void lock(UUID patientId) {
        jdbc.execute((ConnectionCallback<Void>) connection->{
            try(var statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1,"1:patient:"+patientId);statement.execute();
            }
            return null;
        });
    }
    @Override public String state(UUID patientId) {
        return jdbc.query("SELECT state FROM patient_lifecycle WHERE patient_id=?",(rs,n)->rs.getString(1),patientId).stream().findFirst().orElse("ACTIVE");
    }
    @Override public boolean hasAppointments(UUID patientId,boolean activeOnly) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM appointments WHERE patient_id=?
                    AND (?=false OR status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS')))
                """,Boolean.class,patientId,activeOnly));
    }
    @Override public void change(UUID patientId,UUID operation,String state) {
        jdbc.update("""
                INSERT INTO patient_lifecycle(patient_id,state,operation_id) VALUES (?,?,?)
                ON CONFLICT (patient_id) DO UPDATE SET state=EXCLUDED.state,operation_id=EXCLUDED.operation_id,updated_at=now()
                """,patientId,state,operation);
    }
    @Override public void compensate(UUID patientId,UUID operation,String state) {
        jdbc.update("UPDATE patient_lifecycle SET state=?,updated_at=now() WHERE patient_id=? AND operation_id=?",state,patientId,operation);
    }
}
