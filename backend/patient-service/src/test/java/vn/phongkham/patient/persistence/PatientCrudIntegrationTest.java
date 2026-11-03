package vn.phongkham.patient.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.phongkham.common.ApiException;
import vn.phongkham.patient.api.PatientModels;
import vn.phongkham.patient.application.PatientLifecycleClient;
import vn.phongkham.patient.application.PatientService;
import vn.phongkham.patient.application.PatientServiceImpl;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker=true)
class PatientCrudIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18.6-alpine");
    static DataSource dataSource;
    static JdbcTemplate jdbc;
    PatientDao dao;
    PatientLifecycleClient lifecycle;
    PatientService service;
    final UUID actor=UUID.randomUUID();
    @BeforeAll static void migrate() {
        dataSource=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        jdbc=new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE patients,patient_audit_events CASCADE");
        dao=spy(new JdbcPatientDao(jdbc));lifecycle=mock(PatientLifecycleClient.class);
        var proxy=new ProxyFactory(new PatientServiceImpl(dao,lifecycle));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),new AnnotationTransactionAttributeSource()));
        service=(PatientService)proxy.getProxy();
    }
    PatientModels.PatientSummary create() {
        return service.create(new PatientModels.CreatePatientRequest("Test Patient","0900000000",null,"FEMALE",null,"Old address",null,null,"Penicillin","Original clinical history"),actor,"test-create");
    }
    @Test void createReadUpdateDeletePreservesClinicalFieldsAndDeletionAudit() {
        var patient=create();assertEquals(1,service.search("Test",0,20,"ACTIVE").items().size());
        var updated=service.update(patient.id(),new PatientModels.UpdatePatientRequest("Updated Patient","0911111111","new@example.test","FEMALE",null,"New address",null,null),actor,"test-update");
        assertEquals("Updated Patient",updated.fullName());assertEquals("0911111111",updated.phone());
        assertEquals("Penicillin",service.findDetail(patient.id()).allergies());
        assertEquals("Original clinical history",service.findDetail(patient.id()).medicalHistory());
        service.delete(patient.id(),actor,"test-delete","token");
        assertTrue(dao.findSummary(patient.id()).isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM patient_audit_events WHERE patient_id=? AND action='PATIENT_DELETED'",Integer.class,patient.id()));
        verify(lifecycle).change(eq(patient.id()),any(UUID.class),eq("DELETED"),eq("token"));
        verify(lifecycle,never()).compensate(any(),any(),any(),any());
    }
    @Test void archiveKeepsLinkedHistoryReadableAndRestoreEnablesEditing() {
        var patient=create();UUID account=UUID.randomUUID();service.linkAccount(patient.id(),account,actor,"link");
        service.setArchived(patient.id(),true,actor,"archive","token");
        assertTrue(service.search("",0,20,"ACTIVE").items().isEmpty());
        assertEquals(1,service.search("",0,20,"ARCHIVED").items().size());
        assertNotNull(service.findLinkedPatient(account).archivedAt());
        assertEquals("Penicillin",service.findDetail(patient.id()).allergies());
        assertThrows(ApiException.class,()->service.update(patient.id(),new PatientModels.UpdatePatientRequest("Updated Patient","0911111111",null,null,null,null,null,null),actor,"update"));
        service.setArchived(patient.id(),false,actor,"restore","token");
        assertNull(service.findSummary(patient.id()).archivedAt());
        assertEquals(1,service.search("",0,20,"ACTIVE").items().size());
    }
    @Test void linkedPatientCannotBePermanentlyDeleted() {
        var patient=create();service.linkAccount(patient.id(),UUID.randomUUID(),actor,"link");
        assertThrows(ApiException.class,()->service.delete(patient.id(),actor,"delete","token"));
        assertTrue(dao.findSummary(patient.id()).isPresent());verifyNoInteractions(lifecycle);
    }
    @Test void refusedSchedulingGuardRollsBackAndRequestsCompensation() {
        var patient=create();
        doThrow(new ApiException(HttpStatus.CONFLICT,"PATIENT_HAS_APPOINTMENTS","Has history"))
                .when(lifecycle).change(eq(patient.id()),any(),eq("DELETED"),anyString());
        assertThrows(ApiException.class,()->service.delete(patient.id(),actor,"delete","token"));
        assertTrue(dao.findSummary(patient.id()).isPresent());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM patient_audit_events WHERE action='PATIENT_DELETED'",Integer.class));
        verify(lifecycle).compensate(eq(patient.id()),any(),eq("ACTIVE"),eq("token"));
    }
    @Test void localWriteFailureCompensatesRemoteGuardAndRollsBackAudit() {
        var patient=create();doThrow(new DataIntegrityViolationException("Concurrent reference")).when(dao).delete(patient.id());
        assertThrows(ApiException.class,()->service.delete(patient.id(),actor,"delete","token"));
        assertTrue(dao.findSummary(patient.id()).isPresent());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM patient_audit_events WHERE action='PATIENT_DELETED'",Integer.class));
        verify(lifecycle).compensate(eq(patient.id()),any(),eq("ACTIVE"),eq("token"));
    }
}
