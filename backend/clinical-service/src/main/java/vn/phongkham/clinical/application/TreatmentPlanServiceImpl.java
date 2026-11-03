package vn.phongkham.clinical.application;

import org.springframework.http.HttpStatus;
import vn.phongkham.clinical.persistence.TreatmentPlanDao;
import vn.phongkham.clinical.persistence.TreatmentPlanDao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.clinical.api.ClinicalModels;
import vn.phongkham.common.ApiException;

import java.util.List;
import java.util.UUID;

@Service
public class TreatmentPlanServiceImpl implements TreatmentPlanService {
    private final TreatmentPlanDao dao;
    private final SchedulingLookupClient scheduling;
    public TreatmentPlanServiceImpl(TreatmentPlanDao dao,SchedulingLookupClient scheduling){this.dao=dao;this.scheduling=scheduling;}

    @Transactional
    public ClinicalModels.TreatmentPlan create(UUID recordId,String title,UUID doctor,String trace){
        var record=dao.findOwnedRecord(recordId, doctor).stream().findFirst().orElseThrow(this::notFound);
        UUID planId=UUID.randomUUID();
        dao.insertPlan(planId, record, recordId, doctor, title.trim());
        UUID savedId=dao.findRecordPlan(recordId);
        if(!savedId.equals(planId))return get(savedId,doctor);
        audit(doctor,savedId,trace,"TREATMENT_PLAN_CREATED");
        return get(savedId,doctor);
    }

    @Transactional
    public ClinicalModels.TreatmentPlanItem addItem(UUID planId,ClinicalModels.TreatmentPlanItemRequest request,UUID doctor,String token,String trace){
        var plan=dao.findLockedPlanStatus(planId, doctor).stream().findFirst().orElseThrow(this::notFound);
        if(!"DRAFT".equals(plan))throw new ApiException(HttpStatus.CONFLICT,"TREATMENT_PLAN_IMMUTABLE","Kế hoạch đã duyệt và không thể sửa.");
        var service=scheduling.requireService(request.serviceId(),token);
        Integer sequence=dao.nextSequence(planId);
        UUID id=UUID.randomUUID();var quantity=request.quantity()==null?java.math.BigDecimal.ONE:request.quantity();
        dao.insertItem(id, planId, sequence, service.id(), service.name(), quantity, service.price(), request.notes()==null?null:request.notes().trim());
        dao.touchPlan(planId);
        audit(doctor,id,trace,"TREATMENT_PLAN_ITEM_ADDED");
        return new ClinicalModels.TreatmentPlanItem(id,sequence,service.id(),service.name(),quantity,service.price(),request.notes());
    }

    @Transactional
    public ClinicalModels.TreatmentPlan approve(UUID planId,long version,UUID doctor,String trace){
        var parent=dao.findApprovablePlan(planId, doctor, version).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"TREATMENT_PLAN_NOT_APPROVABLE","Kế hoạch đã thay đổi hoặc không còn ở trạng thái bản nháp."));
        int count=dao.countItems(planId);
        if(count==0)throw new ApiException(HttpStatus.CONFLICT,"EMPTY_TREATMENT_PLAN","Thêm ít nhất một hạng mục trước khi duyệt.");
        int signed=dao.countSignedRecord(parent);
        if(signed==0)throw new ApiException(HttpStatus.CONFLICT,"RECORD_MUST_BE_SIGNED","Chỉ duyệt kế hoạch dựa trên bệnh án đã chốt.");
        dao.approvePlan(planId, version);
        audit(doctor,planId,trace,"TREATMENT_PLAN_APPROVED");
        return get(planId,doctor);
    }

    @Transactional(readOnly=true)
    public ClinicalModels.TreatmentPlan get(UUID planId,UUID doctor){
        var plan=dao.findOwnedPlan(planId, doctor).stream().findFirst().orElseThrow(this::notFound);
        var items=dao.listItems(plan.id());
        return new ClinicalModels.TreatmentPlan(plan.id(),plan.recordId(),plan.patientId(),plan.title(),plan.status(),plan.version(),plan.approvedAt(),items);
    }

    @Transactional(readOnly=true)
    public List<ClinicalModels.TreatmentPlan> publishedForPatient(UUID patientId){
        var plans=dao.listPublishedPlans(patientId);
        return plans.stream().map(plan->{
            var items=dao.listPublishedItems(plan.id());
            return new ClinicalModels.TreatmentPlan(plan.id(),plan.recordId(),plan.patientId(),plan.title(),plan.status(),plan.version(),plan.approvedAt(),items);
        }).toList();
    }

    private void audit(UUID actor,UUID id,String trace,String action){dao.insertAudit(actor, action, id, trace, "{}");}
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"TREATMENT_PLAN_NOT_FOUND","Không tìm thấy kế hoạch hoặc lượt khám thuộc bác sĩ hiện tại.");}

}
