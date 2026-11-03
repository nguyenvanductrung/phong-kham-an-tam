package vn.phongkham.patient.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.phongkham.common.ApiException;
import vn.phongkham.patient.api.PatientModels;
import vn.phongkham.patient.persistence.PatientDao;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly=true)
public class PatientServiceImpl implements PatientService {
    private final PatientDao dao;
    private final PatientLifecycleClient lifecycle;
    public PatientServiceImpl(PatientDao dao,PatientLifecycleClient lifecycle) { this.dao=dao;this.lifecycle=lifecycle; }
    @Override public PatientModels.PatientPage search(String query,int page,int size,String status) {
        if(!List.of("ACTIVE","ARCHIVED","ALL").contains(status))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PATIENT_STATUS","Bộ lọc hồ sơ không hợp lệ.");
        int bounded=Math.max(1,Math.min(size,100)),safePage=Math.max(0,page);
        var rows=dao.search(query==null?"":query.trim(),status,bounded+1,(long)safePage*bounded);
        boolean next=rows.size()>bounded;
        return new PatientModels.PatientPage(next?rows.subList(0,bounded):rows,safePage,bounded,next);
    }
    @Override @Transactional
    public PatientModels.PatientSummary create(PatientModels.CreatePatientRequest r,UUID actor,String trace) {
        validate(r.fullName(),r.phone(),r.gender(),r.dateOfBirth());
        UUID id=UUID.randomUUID();dao.insert(id,"BN-"+id.toString().substring(0,8).toUpperCase(),r,actor);
        dao.audit(actor,"PATIENT_CREATED",id,trace);return findSummary(id);
    }
    @Override @Transactional
    public PatientModels.PatientSummary update(UUID id,PatientModels.UpdatePatientRequest r,UUID actor,String trace) {
        requireActive(dao.lock(id).orElseThrow(this::notFound));validate(r.fullName(),r.phone(),r.gender(),r.dateOfBirth());
        if(dao.update(id,r)!=1) throw notFound();
        dao.audit(actor,"PATIENT_UPDATED",id,trace);return findSummary(id);
    }
    @Override @Transactional
    public void delete(UUID id,UUID actor,String trace,String token) {
        var patient=dao.lock(id).orElseThrow(this::notFound);
        if(dao.hasLocalReferences(id)) throw new ApiException(HttpStatus.CONFLICT,"PATIENT_HAS_REFERENCES","Hồ sơ đã được liên kết hoặc sử dụng. Hãy lưu trữ thay vì xóa.");
        changeLifecycle(patient,"DELETED",token);
        // Audit has no patient FK, preserving the deletion evidence.
        dao.audit(actor,"PATIENT_DELETED",id,trace);
        try { if(dao.delete(id)!=1) throw notFound(); }
        catch(DataIntegrityViolationException ex) { throw new ApiException(HttpStatus.CONFLICT,"PATIENT_HAS_REFERENCES","Hồ sơ đã có dữ liệu liên quan và không thể xóa."); }
    }
    @Override @Transactional
    public PatientModels.PatientSummary setArchived(UUID id,boolean archived,UUID actor,String trace,String token) {
        var patient=dao.lock(id).orElseThrow(this::notFound);changeLifecycle(patient,archived?"ARCHIVED":"ACTIVE",token);
        dao.setArchived(id,archived);dao.audit(actor,archived?"PATIENT_ARCHIVED":"PATIENT_RESTORED",id,trace);return findSummary(id);
    }
    private void changeLifecycle(PatientModels.PatientSummary patient,String next,String token) {
        UUID operation=UUID.randomUUID();String previous=patient.archivedAt()==null?"ACTIVE":"ARCHIVED";
        // Register before HTTP: a lost response can still have committed remotely.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) lifecycle.compensate(patient.id(),operation,previous,token);
            }
        });
        lifecycle.change(patient.id(),operation,next,token);
    }
    @Override public PatientModels.PatientSummary findSummary(UUID id) { return dao.findSummary(id).orElseThrow(this::notFound); }
    @Override public PatientModels.PatientDetail findDetail(UUID id) { return dao.findDetail(id).orElseThrow(this::notFound); }
    @Override public boolean isLinkedTo(UUID patientId,UUID userId) { return dao.isLinkedTo(patientId,userId); }
    @Override public PatientModels.PatientSummary findLinkedPatient(UUID userId) {
        return findSummary(dao.findLinkedPatient(userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PATIENT_LINK_NOT_FOUND","Tài khoản chưa được liên kết với hồ sơ bệnh nhân.")));
    }
    @Override @Transactional
    public PatientModels.AccountLink linkAccount(UUID patientId,UUID userId,UUID actor,String trace) {
        requireActive(dao.lock(patientId).orElseThrow(this::notFound));
        try { dao.linkAccount(patientId,userId,actor); }
        catch(DuplicateKeyException ex) { throw new ApiException(HttpStatus.CONFLICT,"PATIENT_ACCOUNT_LINK_CONFLICT","Hồ sơ hoặc tài khoản đã được liên kết với tài khoản khác."); }
        dao.audit(actor,"PATIENT_ACCOUNT_LINKED",patientId,trace);return new PatientModels.AccountLink(true,userId);
    }
    @Override public PatientModels.AccountLink accountLink(UUID patientId) {
        findSummary(patientId);return dao.findLinkedAccount(patientId).map(id->new PatientModels.AccountLink(true,id)).orElse(new PatientModels.AccountLink(false,null));
    }
    @Override public boolean hasDoctorGrant(UUID patientId,UUID doctorId) { return dao.hasDoctorGrant(patientId,doctorId); }
    private static void requireActive(PatientModels.PatientSummary patient) {
        if(patient.archivedAt()!=null) throw new ApiException(HttpStatus.CONFLICT,"PATIENT_ARCHIVED","Hãy khôi phục hồ sơ trước khi sửa hoặc liên kết tài khoản.");
    }
    private static void validate(String name,String phone,String gender,LocalDate birthday) {
        if(name==null||name.trim().length()<2||phone==null||phone.trim().length()<8)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PATIENT","Họ tên phải có ít nhất 2 ký tự và số điện thoại ít nhất 8 ký tự.");
        if(gender!=null&&!gender.isBlank()&&!List.of("MALE","FEMALE","OTHER").contains(gender))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_GENDER","Giới tính không hợp lệ.");
        if(birthday!=null&&birthday.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BIRTH_DATE","Ngày sinh không được nằm trong tương lai.");
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"PATIENT_NOT_FOUND","Không tìm thấy hồ sơ bệnh nhân."); }
}
