package vn.phongkham.scheduling.application;

import vn.phongkham.scheduling.api.SchedulingAdminModels;
import java.util.UUID;

/** Application use cases consumed by controllers and other services. */
public interface SchedulingAdminService {
    SchedulingAdminModels.CatalogSnapshot snapshot();
    SchedulingAdminModels.ServiceItem saveService(UUID id, SchedulingAdminModels.ServiceRequest request, UUID actor, String requestId);
    SchedulingAdminModels.ChairItem saveChair(UUID id, SchedulingAdminModels.ChairRequest request, UUID actor, String requestId);
    SchedulingAdminModels.WorkingHourItem addWorkingHour(SchedulingAdminModels.WorkingHourRequest request, UUID actor, String requestId);
    void deleteWorkingHour(UUID id, UUID actor, String requestId);
    SchedulingAdminModels.LeaveItem requestLeave(SchedulingAdminModels.LeaveRequest request, UUID actor, String requestId);
    SchedulingAdminModels.LeaveItem reviewLeave(UUID id, SchedulingAdminModels.LeaveReviewRequest request, UUID actor, String requestId);
    SchedulingAdminModels.ClosureItem addClosure(SchedulingAdminModels.ClosureRequest request, UUID actor, String requestId);
    void deleteClosure(UUID id, UUID actor, String requestId);

}
