package vn.phongkham.scheduling.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.persistence.PatientLifecycleDao;
import java.util.UUID;

@Service
public class PatientLifecycleServiceImpl implements PatientLifecycleService {
    private final PatientLifecycleDao dao;
    public PatientLifecycleServiceImpl(PatientLifecycleDao dao) { this.dao=dao; }
    @Override @Transactional
    public void change(UUID patientId,UUID operation,String state) {
        dao.lock(patientId);
        if((state.equals("DELETED")&&dao.hasAppointments(patientId,false))
                ||(state.equals("ARCHIVED")&&dao.hasAppointments(patientId,true)))
            throw new ApiException(HttpStatus.CONFLICT,"PATIENT_HAS_APPOINTMENTS","Hồ sơ có lịch hẹn chưa đáp ứng điều kiện xóa/lưu trữ.");
        // Only patient-service invokes this internal contract after locking an existing
        // patient. Explicit restore also repairs a guard left blocked by a failed rollback.
        dao.change(patientId,operation,state);
    }
    @Override @Transactional
    public void compensate(UUID patientId,UUID operation,String state) {
        dao.lock(patientId);dao.compensate(patientId,operation,state);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void requireActive(UUID patientId) {
        dao.lock(patientId);
        if(!dao.state(patientId).equals("ACTIVE"))
            throw new ApiException(HttpStatus.CONFLICT,"PATIENT_INACTIVE","Hồ sơ đã lưu trữ hoặc xóa, không thể đặt hay đổi lịch.");
    }
}
