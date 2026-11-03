package vn.phongkham.scheduling.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingModels;
import vn.phongkham.scheduling.application.*;
import vn.phongkham.scheduling.domain.AppointmentStateMachine;
import java.time.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class PatientLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18.6-alpine");
    static JdbcTemplate jdbc;static TransactionTemplate tx;
    final UUID patient=UUID.randomUUID(),doctor=UUID.randomUUID(),chair=UUID.randomUUID(),service=UUID.randomUUID(),actor=UUID.randomUUID();
    final Instant slot=Instant.parse("2030-01-01T03:00:00Z");
    PatientLifecycleDao dao;PatientLifecycleService lifecycle;AppointmentWriter writer;
    @BeforeAll static void migrate() {
        var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE patient_lifecycle,idempotency_records,appointments,doctors,chairs,services,clinic_settings,outbox_events,audit_events CASCADE");
        jdbc.update("INSERT INTO clinic_settings(id) VALUES (1)");
        jdbc.update("INSERT INTO doctors(user_id,display_name,license_number) VALUES (?,'Doctor','TEST')",doctor);
        jdbc.update("INSERT INTO chairs(id,name) VALUES (?,'Chair')",chair);
        jdbc.update("INSERT INTO services(id,code,name,price,duration_minutes) VALUES (?,'TEST','Test',100000,30)",service);
        jdbc.update("INSERT INTO doctor_working_hours(doctor_id,day_of_week,starts_at,ends_at) VALUES (?,?,'08:00','17:00')",doctor,slot.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).getDayOfWeek().getValue());
        dao=new JdbcPatientLifecycleDao(jdbc);lifecycle=new PatientLifecycleServiceImpl(dao);
        writer=new AppointmentWriterImpl(new JdbcAppointmentDao(jdbc),JsonMapper.builder().build(),new AppointmentStateMachine(),"Asia/Ho_Chi_Minh",lifecycle);
    }
    SchedulingModels.AppointmentResponse book() {
        return writer.book(new SchedulingModels.CreateAppointmentRequest(patient,doctor,chair,service,slot,30,null,null),"request-key",actor,Set.of("RECEPTIONIST"),"trace",
                new PatientLookupClient.PatientSummary(patient,"BN-TEST","Patient","0900000000",null,null,null,null,null,null,Instant.now(),null));
    }
    @Test void staleBookingRequestCannotPassCommittedDeleteGuard() {
        tx.executeWithoutResult(s->lifecycle.change(patient,UUID.randomUUID(),"DELETED"));
        assertThrows(ApiException.class,()->tx.execute(s->book()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM appointments",Integer.class));
    }
    @Test void archiveRejectsPendingAppointmentsAndPreservesCancelledHistory() {
        var appointment=tx.execute(s->book());
        assertThrows(ApiException.class,()->tx.executeWithoutResult(s->lifecycle.change(patient,UUID.randomUUID(),"ARCHIVED")));
        assertEquals("ACTIVE",dao.state(patient));
        jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE id=?",appointment.id());
        tx.executeWithoutResult(s->lifecycle.change(patient,UUID.randomUUID(),"ARCHIVED"));
        assertEquals(1,tx.execute(s->writer.history(patient,actor,Set.of("RECEPTIONIST"),100)).size());
        assertThrows(ApiException.class,()->tx.executeWithoutResult(s->lifecycle.change(patient,UUID.randomUUID(),"DELETED")));
    }
    @Test void archiveWaitsForInFlightBookingAndThenRejects() throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        var bookingWritten=new CountDownLatch(1);var allowCommit=new CountDownLatch(1);
        try {
            var booking=executor.submit(()->tx.execute(s->{var result=book();bookingWritten.countDown();await(allowCommit);return result;}));
            assertTrue(bookingWritten.await(15,TimeUnit.SECONDS));
            var archive=executor.submit(()->tx.execute(s->{lifecycle.change(patient,UUID.randomUUID(),"ARCHIVED");return true;}));
            allowCommit.countDown();assertNotNull(booking.get(15,TimeUnit.SECONDS));
            var failure=assertThrows(ExecutionException.class,()->archive.get(15,TimeUnit.SECONDS));
            assertInstanceOf(ApiException.class,failure.getCause());assertEquals("ACTIVE",dao.state(patient));
        } finally { allowCommit.countDown();executor.shutdownNow(); }
    }
    @Test void compensationCannotUndoANewerLifecycleOperation() {
        UUID old=UUID.randomUUID(),latest=UUID.randomUUID();
        tx.executeWithoutResult(s->lifecycle.change(patient,old,"ARCHIVED"));
        tx.executeWithoutResult(s->lifecycle.change(patient,latest,"ARCHIVED"));
        tx.executeWithoutResult(s->lifecycle.compensate(patient,old,"ACTIVE"));
        assertEquals("ARCHIVED",dao.state(patient));
        tx.executeWithoutResult(s->lifecycle.change(patient,UUID.randomUUID(),"ACTIVE"));
        assertNotNull(tx.execute(s->book()));
    }
    static void await(CountDownLatch latch) {
        try { if(!latch.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Timed out awaiting commit"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException(ex); }
    }
}
