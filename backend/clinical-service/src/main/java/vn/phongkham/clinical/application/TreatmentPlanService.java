package vn.phongkham.clinical.application;

import vn.phongkham.clinical.api.ClinicalModels;
import java.util.List;
import java.util.UUID;

/** Application use cases consumed by controllers and other services. */
public interface TreatmentPlanService {
    ClinicalModels.TreatmentPlan create(UUID recordId,String title,UUID doctor,String trace);
    ClinicalModels.TreatmentPlanItem addItem(UUID planId,ClinicalModels.TreatmentPlanItemRequest request,UUID doctor,String token,String trace);
    ClinicalModels.TreatmentPlan approve(UUID planId,long version,UUID doctor,String trace);
    ClinicalModels.TreatmentPlan get(UUID planId,UUID doctor);
    List<ClinicalModels.TreatmentPlan> publishedForPatient(UUID patientId);

}
