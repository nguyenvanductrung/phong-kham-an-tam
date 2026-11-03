package vn.phongkham.clinical.persistence;

import java.time.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.clinical.persistence.TreatmentPlanDao.*;

@Repository
public class JdbcTreatmentPlanDao implements TreatmentPlanDao {
    private final JdbcTemplate jdbc;
    public JdbcTreatmentPlanDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<UUID> findOwnedRecord(UUID record, UUID doctor) {
        return jdbc.query("SELECT patient_id FROM clinical_records WHERE id=? AND doctor_id=?", (rs,n)->rs.getObject("patient_id",UUID.class), new Object[]{record, doctor});
    }

    @Override
    public int insertPlan(UUID id, UUID patient, UUID record, UUID doctor, String title) {
        return jdbc.update("INSERT INTO treatment_plans(id,patient_id,clinical_record_id,doctor_id,title) VALUES (?,?,?,?,?) ON CONFLICT (clinical_record_id) DO NOTHING", new Object[]{id, patient, record, doctor, title});
    }

    @Override
    public UUID findRecordPlan(UUID record) {
        return jdbc.queryForObject("SELECT id FROM treatment_plans WHERE clinical_record_id=?", UUID.class, new Object[]{record});
    }

    @Override
    public List<String> findLockedPlanStatus(UUID plan, UUID doctor) {
        return jdbc.query("SELECT status FROM treatment_plans WHERE id=? AND doctor_id=? FOR UPDATE", (rs,n)->rs.getString("status"), new Object[]{plan, doctor});
    }

    @Override
    public Integer nextSequence(UUID plan) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM treatment_plan_items WHERE plan_id=?", Integer.class, new Object[]{plan});
    }

    @Override
    public int insertItem(UUID id, UUID plan, int sequence, UUID service, String title, BigDecimal quantity, BigDecimal price, String notes) {
        return jdbc.update("INSERT INTO treatment_plan_items(id,plan_id,sequence_no,service_id,title_snapshot,quantity,unit_price,notes) VALUES (?,?,?,?,?,?,?,?)", new Object[]{id, plan, sequence, service, title, quantity, price, notes});
    }

    @Override
    public int touchPlan(UUID plan) {
        return jdbc.update("UPDATE treatment_plans SET version=version+1,updated_at=now() WHERE id=?", new Object[]{plan});
    }

    @Override
    public List<UUID> findApprovablePlan(UUID plan, UUID doctor, long version) {
        return jdbc.query("SELECT clinical_record_id FROM treatment_plans WHERE id=? AND doctor_id=? AND status='DRAFT' AND version=? FOR UPDATE", (rs,n)->rs.getObject("clinical_record_id",UUID.class), new Object[]{plan, doctor, version});
    }

    @Override
    public Integer countItems(UUID plan) {
        return jdbc.queryForObject("SELECT count(*) FROM treatment_plan_items WHERE plan_id=?", Integer.class, new Object[]{plan});
    }

    @Override
    public Integer countSignedRecord(UUID record) {
        return jdbc.queryForObject("SELECT count(*) FROM clinical_records WHERE id=? AND status='SIGNED'", Integer.class, new Object[]{record});
    }

    @Override
    public int approvePlan(UUID plan, long version) {
        return jdbc.update("UPDATE treatment_plans SET status='APPROVED',approved_at=now(),version=version+1,updated_at=now() WHERE id=? AND version=?", new Object[]{plan, version});
    }

    @Override
    public List<Plan> findOwnedPlan(UUID plan, UUID doctor) {
        return jdbc.query("SELECT id,clinical_record_id,patient_id,title,status,version,approved_at FROM treatment_plans WHERE id=? AND doctor_id=?", (rs,n)->new Plan(rs.getObject("id",UUID.class),rs.getObject("clinical_record_id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getString("title"),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("approved_at")==null?null:rs.getTimestamp("approved_at").toInstant()), new Object[]{plan, doctor});
    }

    @Override
    public List<ClinicalModels.TreatmentPlanItem> listItems(UUID plan) {
        return jdbc.query("SELECT id,sequence_no,service_id,title_snapshot,quantity,unit_price,notes FROM treatment_plan_items WHERE plan_id=? ORDER BY sequence_no", (rs,n)->new ClinicalModels.TreatmentPlanItem(rs.getObject("id",UUID.class),rs.getInt("sequence_no"),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getString("notes")), new Object[]{plan});
    }

    @Override
    public List<Plan> listPublishedPlans(UUID patient) {
        return jdbc.query("SELECT id,clinical_record_id,patient_id,title,status,version,approved_at FROM treatment_plans WHERE patient_id=? AND status='APPROVED' ORDER BY approved_at DESC LIMIT 100", (rs,n)->new Plan(rs.getObject("id",UUID.class),rs.getObject("clinical_record_id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getString("title"),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("approved_at").toInstant()), new Object[]{patient});
    }

    @Override
    public List<ClinicalModels.TreatmentPlanItem> listPublishedItems(UUID plan) {
        return jdbc.query("SELECT id,sequence_no,service_id,title_snapshot,quantity,unit_price,notes FROM treatment_plan_items WHERE plan_id=? ORDER BY sequence_no", (rs,n)->new ClinicalModels.TreatmentPlanItem(rs.getObject("id",UUID.class),rs.getInt("sequence_no"),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getString("notes")), new Object[]{plan});
    }

    @Override
    public int insertAudit(UUID actor, String action, UUID entity, String trace, String metadata) {
        return jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?, 'DOCTOR', ?, 'TreatmentPlan', ?,?,?::jsonb)", new Object[]{actor, action, entity, trace, metadata});
    }
}
