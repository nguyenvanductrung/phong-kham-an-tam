package vn.phongkham.clinical.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.phongkham.clinical.application.ClinicalRecordWriter;
import vn.phongkham.clinical.application.ClinicalFileService;
import vn.phongkham.clinical.application.TreatmentPlanService;
import vn.phongkham.clinical.application.PatientLookupClient;
import vn.phongkham.clinical.application.SchedulingLookupClient;
import vn.phongkham.common.ApiException;
import vn.phongkham.common.CorrelationIdFilter;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalController {
    private final ClinicalRecordWriter records;
    private final ClinicalFileService files;
    private final TreatmentPlanService treatmentPlans;
    private final SchedulingLookupClient scheduling;
    private final PatientLookupClient patients;
    public ClinicalController(ClinicalRecordWriter records,ClinicalFileService files,TreatmentPlanService treatmentPlans,SchedulingLookupClient scheduling,PatientLookupClient patients){this.records=records;this.files=files;this.treatmentPlans=treatmentPlans;this.scheduling=scheduling;this.patients=patients;}

    @PostMapping("/records/from-appointment") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.RecordResponse create(@Valid @RequestBody ClinicalModels.FromAppointmentRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");var appointment=scheduling.requireAppointment(body.appointmentId(),jwt.getTokenValue());
        return records.createFromAppointment(appointment,actor(jwt),correlation(request),body.chiefComplaint());
    }
    @PutMapping("/records/{id}")
    public ClinicalModels.RecordResponse save(@PathVariable UUID id,@Valid @RequestBody ClinicalModels.SaveDraftRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return records.saveDraft(id,body,actor(jwt),correlation(request));
    }
    @PostMapping("/records/{id}/amendments") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.RecordResponse amendment(@PathVariable UUID id,@Valid @RequestBody ClinicalModels.AmendmentRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return records.createAmendment(id,body.reason(),actor(jwt),correlation(request));
    }
    @PostMapping("/records/{id}/teeth") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.ToothResponse tooth(@PathVariable UUID id,@Valid @RequestBody ClinicalModels.ToothRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return records.addTooth(id,body,actor(jwt),correlation(request));
    }
    @PostMapping("/records/{id}/procedures") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.ProcedureResponse procedure(@PathVariable UUID id,@Valid @RequestBody ClinicalModels.ProcedureRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");var service=scheduling.requireService(body.serviceId(),jwt.getTokenValue());return records.addProcedure(id,body,service,actor(jwt),correlation(request));
    }
    @PostMapping("/records/{id}/sign")
    public ClinicalModels.RecordResponse sign(@PathVariable UUID id,@RequestParam long version,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return records.sign(id,actor(jwt),correlation(request),version);
    }
    @PostMapping("/records/{id}/prescriptions") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.PrescriptionResponse prescription(@PathVariable UUID id,@Valid @RequestBody ClinicalModels.IssuePrescriptionRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return records.issuePrescription(id,body,actor(jwt),correlation(request));
    }
    @GetMapping("/records/{id}")
    public ClinicalModels.RecordResponse get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return records.get(id,actor(jwt),roles(jwt));}
    @GetMapping("/records/{id}/workspace")
    public ClinicalModels.RecordWorkspace workspace(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return records.workspace(id,actor(jwt),roles(jwt));}
    @GetMapping("/appointments/{appointmentId}/record")
    public ClinicalModels.RecordWorkspace appointmentRecord(@PathVariable UUID appointmentId,@AuthenticationPrincipal Jwt jwt){
        requireRole(jwt,"DOCTOR");var appointment=scheduling.requireAppointment(appointmentId,jwt.getTokenValue());
        if(!appointment.doctorId().equals(actor(jwt)))throw new ApiException(HttpStatus.FORBIDDEN,"DOCTOR_NOT_ASSIGNED","Chỉ bác sĩ phụ trách mới xem được bệnh án của lượt khám.");
        return records.workspaceByAppointment(appointmentId,actor(jwt));
    }
    @GetMapping("/patients/{patientId}/records")
    public Object history(@PathVariable UUID patientId,@AuthenticationPrincipal Jwt jwt){
        Set<String> roles=roles(jwt);UUID selected=patientId;UUID doctor=null;boolean signedOnly=false;
        if(roles.contains("PATIENT")){selected=patients.current(jwt.getTokenValue()).id();signedOnly=true;}
        else if(roles.contains("DOCTOR")){doctor=actor(jwt);}
        else throw forbidden();
        if(roles.contains("PATIENT"))patients.requirePatientAccess(selected,jwt.getTokenValue());
        if(signedOnly)return records.publishedHistory(selected);
        return records.history(selected,doctor,signedOnly);
    }
    @GetMapping("/patients/{patientId}/records/{recordId}")
    public ClinicalModels.PublishedRecord published(@PathVariable UUID patientId,@PathVariable UUID recordId,@AuthenticationPrincipal Jwt jwt){
        if(!roles(jwt).contains("PATIENT"))throw forbidden();UUID selected=patients.current(jwt.getTokenValue()).id();
        if(!selected.equals(patientId))throw forbidden();patients.requirePatientAccess(selected,jwt.getTokenValue());
        return records.publishedRecord(recordId,selected);
    }

    @GetMapping("/patients/{patientId}/treatment-plans")
    public Object publishedTreatmentPlans(@PathVariable UUID patientId,@AuthenticationPrincipal Jwt jwt){
        if(!roles(jwt).contains("PATIENT"))throw forbidden();UUID selected=patients.current(jwt.getTokenValue()).id();
        if(!selected.equals(patientId))throw forbidden();patients.requirePatientAccess(selected,jwt.getTokenValue());
        return treatmentPlans.publishedForPatient(selected);
    }

    @PostMapping(value="/records/{id}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.FileResponse upload(@PathVariable UUID id,@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam String fileKind,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return files.upload(id,file,fileKind,actor(jwt),correlation(request));
    }

    @GetMapping("/records/{id}/files")
    public Object files(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
        Set<String> roles=roles(jwt);
        if(roles.contains("PATIENT")){var patient=patients.current(jwt.getTokenValue());patients.requirePatientAccess(patient.id(),jwt.getTokenValue());return files.list(id,actor(jwt),true,patient.id());}
        requireRole(jwt,"DOCTOR");return files.list(id,actor(jwt),false,null);
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> file(@PathVariable UUID fileId,@AuthenticationPrincipal Jwt jwt){
        Set<String> roles=roles(jwt);ClinicalFileService.Download download;
        if(roles.contains("PATIENT")){var patient=patients.current(jwt.getTokenValue());patients.requirePatientAccess(patient.id(),jwt.getTokenValue());download=files.download(fileId,actor(jwt),true,patient.id());}
        else {requireRole(jwt,"DOCTOR");download=files.download(fileId,actor(jwt),false,null);}
        var disposition=ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(new InputStreamResource(download.stream()));
    }

    @PostMapping("/records/{id}/treatment-plan") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.TreatmentPlan createTreatmentPlan(@PathVariable UUID id,@Valid @RequestBody ClinicalModels.CreateTreatmentPlanRequest body,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return treatmentPlans.create(id,body.title(),actor(jwt),correlation(request));
    }

    @PostMapping("/treatment-plans/{planId}/items") @ResponseStatus(HttpStatus.CREATED)
    public ClinicalModels.TreatmentPlanItem addTreatmentPlanItem(@PathVariable UUID planId,@Valid @RequestBody ClinicalModels.TreatmentPlanItemRequest body,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return treatmentPlans.addItem(planId,body,actor(jwt),jwt.getTokenValue(),correlation(request));
    }

    @PostMapping("/treatment-plans/{planId}/approve")
    public ClinicalModels.TreatmentPlan approveTreatmentPlan(@PathVariable UUID planId,@RequestParam long version,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireRole(jwt,"DOCTOR");return treatmentPlans.approve(planId,version,actor(jwt),correlation(request));
    }

    @GetMapping("/treatment-plans/{planId}")
    public ClinicalModels.TreatmentPlan getTreatmentPlan(@PathVariable UUID planId,@AuthenticationPrincipal Jwt jwt){
        requireRole(jwt,"DOCTOR");return treatmentPlans.get(planId,actor(jwt));
    }

    private static UUID actor(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private static Set<String> roles(Jwt jwt){var r=jwt.getClaimAsStringList("roles");return r==null?Set.of():Set.copyOf(r);}
    private static void requireRole(Jwt jwt,String role){if(!roles(jwt).contains(role))throw forbidden();}
    private static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền thực hiện thao tác lâm sàng này.");}
    private static String correlation(HttpServletRequest req){Object id=req.getAttribute(CorrelationIdFilter.HEADER);return id==null?UUID.randomUUID().toString():id.toString();}
}

