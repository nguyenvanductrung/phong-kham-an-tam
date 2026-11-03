package vn.phongkham.clinical.persistence;

import java.time.*;
import java.math.BigDecimal;
import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcTreatmentPlanDao. */
public interface TreatmentPlanDao {
    List<UUID> findOwnedRecord(UUID record, UUID doctor);
    int insertPlan(UUID id, UUID patient, UUID record, UUID doctor, String title);
    UUID findRecordPlan(UUID record);
    List<String> findLockedPlanStatus(UUID plan, UUID doctor);
    Integer nextSequence(UUID plan);
    int insertItem(UUID id, UUID plan, int sequence, UUID service, String title, BigDecimal quantity, BigDecimal price, String notes);
    int touchPlan(UUID plan);
    List<UUID> findApprovablePlan(UUID plan, UUID doctor, long version);
    Integer countItems(UUID plan);
    Integer countSignedRecord(UUID record);
    int approvePlan(UUID plan, long version);
    List<Plan> findOwnedPlan(UUID plan, UUID doctor);
    List<ClinicalModels.TreatmentPlanItem> listItems(UUID plan);
    List<Plan> listPublishedPlans(UUID patient);
    List<ClinicalModels.TreatmentPlanItem> listPublishedItems(UUID plan);
    int insertAudit(UUID actor, String action, UUID entity, String trace, String metadata);
    record Plan(UUID id,UUID recordId,UUID patientId,String title,String status,long version,java.time.Instant approvedAt){}
}
