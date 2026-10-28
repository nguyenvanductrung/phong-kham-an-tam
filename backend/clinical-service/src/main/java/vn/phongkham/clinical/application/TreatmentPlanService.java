package vn.phongkham.clinical.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.clinical.api.ClinicalModels;
import vn.phongkham.common.ApiException;

import java.util.List;
import java.util.UUID;

@Service
public class TreatmentPlanService {
    private final JdbcTemplate jdbc;
    private final SchedulingLookupClient scheduling;
    TreatmentPlanService(JdbcTemplate jdbc,SchedulingLookupClient scheduling){this.jdbc=jdbc;this.scheduling=scheduling;}

    @Transactional
    public ClinicalModels.TreatmentPlan create(UUID recordId,String title,UUID doctor,String trace){
        var record=jdbc.query("SELECT patient_id FROM clinical_records WHERE id=? AND doctor_id=?",
                (rs,n)->rs.getObject("patient_id",UUID.class),recordId,doctor).stream().findFirst().orElseThrow(this::notFound);
        UUID planId=UUID.randomUUID();
        jdbc.update("INSERT INTO treatment_plans(id,patient_id,clinical_record_id,doctor_id,title) VALUES (?,?,?,?,?) ON CONFLICT (clinical_record_id) DO NOTHING",planId,record,recordId,doctor,title.trim());
        UUID savedId=jdbc.queryForObject("SELECT id FROM treatment_plans WHERE clinical_record_id=?",UUID.class,recordId);
        if(!savedId.equals(planId))return get(savedId,doctor);
        audit(doctor,savedId,trace,"TREATMENT_PLAN_CREATED");
        return get(savedId,doctor);
    }

    @Transactional
    public ClinicalModels.TreatmentPlanItem addItem(UUID planId,ClinicalModels.TreatmentPlanItemRequest request,UUID doctor,String token,String trace){
        var plan=jdbc.query("SELECT status FROM treatment_plans WHERE id=? AND doctor_id=? FOR UPDATE",
                (rs,n)->rs.getString("status"),planId,doctor).stream().findFirst().orElseThrow(this::notFound);
        if(!"DRAFT".equals(plan))throw new ApiException(HttpStatus.CONFLICT,"TREATMENT_PLAN_IMMUTABLE","Kế hoạch đã duyệt và không thể sửa.");
        var service=scheduling.requireService(request.serviceId(),token);
        Integer sequence=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM treatment_plan_items WHERE plan_id=?",Integer.class,planId);
        UUID id=UUID.randomUUID();var quantity=request.quantity()==null?java.math.BigDecimal.ONE:request.quantity();
        jdbc.update("INSERT INTO treatment_plan_items(id,plan_id,sequence_no,service_id,title_snapshot,quantity,unit_price,notes) VALUES (?,?,?,?,?,?,?,?)",
                id,planId,sequence,service.id(),service.name(),quantity,service.price(),request.notes()==null?null:request.notes().trim());
        jdbc.update("UPDATE treatment_plans SET version=version+1,updated_at=now() WHERE id=?",planId);
        audit(doctor,id,trace,"TREATMENT_PLAN_ITEM_ADDED");
        return new ClinicalModels.TreatmentPlanItem(id,sequence,service.id(),service.name(),quantity,service.price(),request.notes());
    }

    @Transactional
    public ClinicalModels.TreatmentPlan approve(UUID planId,long version,UUID doctor,String trace){
        var parent=jdbc.query("SELECT clinical_record_id FROM treatment_plans WHERE id=? AND doctor_id=? AND status='DRAFT' AND version=? FOR UPDATE",
                (rs,n)->rs.getObject("clinical_record_id",UUID.class),planId,doctor,version).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"TREATMENT_PLAN_NOT_APPROVABLE","Kế hoạch đã thay đổi hoặc không còn ở trạng thái bản nháp."));
        int count=jdbc.queryForObject("SELECT count(*) FROM treatment_plan_items WHERE plan_id=?",Integer.class,planId);
        if(count==0)throw new ApiException(HttpStatus.CONFLICT,"EMPTY_TREATMENT_PLAN","Thêm ít nhất một hạng mục trước khi duyệt.");
        int signed=jdbc.queryForObject("SELECT count(*) FROM clinical_records WHERE id=? AND status='SIGNED'",Integer.class,parent);
        if(signed==0)throw new ApiException(HttpStatus.CONFLICT,"RECORD_MUST_BE_SIGNED","Chỉ duyệt kế hoạch dựa trên bệnh án đã chốt.");
        jdbc.update("UPDATE treatment_plans SET status='APPROVED',approved_at=now(),version=version+1,updated_at=now() WHERE id=? AND version=?",planId,version);
        audit(doctor,planId,trace,"TREATMENT_PLAN_APPROVED");
        return get(planId,doctor);
    }

    @Transactional(readOnly=true)
    public ClinicalModels.TreatmentPlan get(UUID planId,UUID doctor){
        var plan=jdbc.query("SELECT id,clinical_record_id,patient_id,title,status,version,approved_at FROM treatment_plans WHERE id=? AND doctor_id=?",
                (rs,n)->new Plan(rs.getObject("id",UUID.class),rs.getObject("clinical_record_id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getString("title"),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("approved_at")==null?null:rs.getTimestamp("approved_at").toInstant()),planId,doctor).stream().findFirst().orElseThrow(this::notFound);
        var items=jdbc.query("SELECT id,sequence_no,service_id,title_snapshot,quantity,unit_price,notes FROM treatment_plan_items WHERE plan_id=? ORDER BY sequence_no",
                (rs,n)->new ClinicalModels.TreatmentPlanItem(rs.getObject("id",UUID.class),rs.getInt("sequence_no"),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getString("notes")),plan.id());
        return new ClinicalModels.TreatmentPlan(plan.id(),plan.recordId(),plan.patientId(),plan.title(),plan.status(),plan.version(),plan.approvedAt(),items);
    }

    @Transactional(readOnly=true)
    public List<ClinicalModels.TreatmentPlan> publishedForPatient(UUID patientId){
        var plans=jdbc.query("SELECT id,clinical_record_id,patient_id,title,status,version,approved_at FROM treatment_plans WHERE patient_id=? AND status='APPROVED' ORDER BY approved_at DESC LIMIT 100",
                (rs,n)->new Plan(rs.getObject("id",UUID.class),rs.getObject("clinical_record_id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getString("title"),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("approved_at").toInstant()),patientId);
        return plans.stream().map(plan->{
            var items=jdbc.query("SELECT id,sequence_no,service_id,title_snapshot,quantity,unit_price,notes FROM treatment_plan_items WHERE plan_id=? ORDER BY sequence_no",
                    (rs,n)->new ClinicalModels.TreatmentPlanItem(rs.getObject("id",UUID.class),rs.getInt("sequence_no"),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getString("notes")),plan.id());
            return new ClinicalModels.TreatmentPlan(plan.id(),plan.recordId(),plan.patientId(),plan.title(),plan.status(),plan.version(),plan.approvedAt(),items);
        }).toList();
    }

    private void audit(UUID actor,UUID id,String trace,String action){jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?, 'DOCTOR', ?, 'TreatmentPlan', ?,?,?::jsonb)",actor,action,id,trace,"{}");}
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"TREATMENT_PLAN_NOT_FOUND","Không tìm thấy kế hoạch hoặc lượt khám thuộc bác sĩ hiện tại.");}
    private record Plan(UUID id,UUID recordId,UUID patientId,String title,String status,long version,java.time.Instant approvedAt){}
}
