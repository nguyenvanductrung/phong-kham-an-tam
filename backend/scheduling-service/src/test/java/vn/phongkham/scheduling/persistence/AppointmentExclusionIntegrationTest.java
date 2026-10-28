package vn.phongkham.scheduling.persistence;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingAdminModels;
import vn.phongkham.scheduling.application.SchedulingAdminService;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class AppointmentExclusionIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18.6-alpine");
    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static TransactionTemplate transaction;
    static final UUID DOCTOR=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID CHAIR=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static final UUID ACTOR=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @BeforeAll static void migrate(){
        dataSource=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        jdbc=new JdbcTemplate(dataSource);transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }
    @AfterAll static void close(){ }
    @BeforeEach void reset(){
        jdbc.execute("TRUNCATE idempotency_records,appointment_services,appointments,doctor_working_hours,doctors,chairs,clinic_settings CASCADE");
        jdbc.update("INSERT INTO clinic_settings(id) VALUES (1)");
        jdbc.update("INSERT INTO doctors(user_id,display_name,license_number) VALUES (?, 'Test Doctor','TEST-1')",DOCTOR);
        jdbc.update("INSERT INTO chairs(id,name) VALUES (?, 'Chair 1')",CHAIR);
    }

    @Test void concurrentWritersForOneSlotProduceExactlyOneSuccess(){
        int requestCount=8;ExecutorService executor=Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready=new CountDownLatch(requestCount),start=new CountDownLatch(1);List<Future<Boolean>> results=new ArrayList<>();
        Instant slot=Instant.parse("2030-01-01T03:00:00Z");
        for(int i=0;i<requestCount;i++){
            final int n=i;
            results.add(executor.submit(()->{
                ready.countDown();start.await();
                try {
                    UUID patient=UUID.randomUUID();
                    transaction.execute(status->{lockResourceKeys("0:doctor:"+DOCTOR,"1:patient:"+patient,"2:chair:"+CHAIR);
                        jdbc.update("INSERT INTO appointments(appointment_number,patient_id,doctor_id,chair_id,scheduled_at,duration_minutes,created_by) VALUES (?,?,?,?,?,30,?)", "AP-"+n,patient,DOCTOR,CHAIR,slot.atOffset(ZoneOffset.UTC),ACTOR);return true;});
                    return true;
                } catch(DataIntegrityViolationException ex) { assertEquals("23P01",sqlState(ex));return false; }
            }));
        }
        try{
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            long successes=0;for(Future<Boolean> result:results) if(result.get(20,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM appointments",Integer.class));
        }catch(Exception ex){throw new AssertionError(ex);}finally{executor.shutdownNow();}
    }

    @Test void adjacentRangesAreAllowed(){
        Instant first=Instant.parse("2030-01-01T03:00:00Z");
        insert(UUID.randomUUID(),first,30);
        assertDoesNotThrow(()->insert(UUID.randomUUID(),first.plusSeconds(30*60L),30));
    }

    @Test void patientCannotBeBookedWithTwoDoctorsAtTheSameTime(){
        Instant slot=Instant.parse("2030-01-01T03:00:00Z");
        UUID patient=UUID.randomUUID(),otherDoctor=UUID.randomUUID(),otherChair=UUID.randomUUID();
        jdbc.update("INSERT INTO doctors(user_id,display_name,license_number) VALUES (?, 'Other Doctor','TEST-2')",otherDoctor);
        jdbc.update("INSERT INTO chairs(id,name) VALUES (?, 'Chair 2')",otherChair);
        insert(patient,slot,30);
        assertEquals("23P01",sqlState(assertThrows(DataIntegrityViolationException.class,
                ()->insert(patient,slot,30,otherDoctor,otherChair))));
    }

    @Test void chairCannotBeUsedByTwoDoctorsAtTheSameTime(){
        Instant slot=Instant.parse("2030-01-01T03:00:00Z");
        UUID otherDoctor=UUID.randomUUID();
        jdbc.update("INSERT INTO doctors(user_id,display_name,license_number) VALUES (?, 'Other Doctor','TEST-2')",otherDoctor);
        insert(UUID.randomUUID(),slot,30);
        assertEquals("23P01",sqlState(assertThrows(DataIntegrityViolationException.class,
                ()->insert(UUID.randomUUID(),slot,30,otherDoctor,CHAIR))));
    }

    @Test void failedRescheduleRollsBackAndKeepsOldSlot(){
        Instant old=Instant.parse("2030-01-01T03:00:00Z"),occupied=old.plusSeconds(60*60L);
        UUID first=insert(UUID.randomUUID(),old,30);insert(UUID.randomUUID(),occupied,30);
        assertThrows(DataIntegrityViolationException.class,()->transaction.execute(status->{jdbc.update("UPDATE appointments SET scheduled_at=? WHERE id=?",occupied.atOffset(ZoneOffset.UTC),first);return null;}));
        assertEquals(old,jdbc.queryForObject("SELECT scheduled_at FROM appointments WHERE id=?",(rs,n)->rs.getTimestamp(1).toInstant(),first));
    }

    @Test void workingHoursRejectOverlappingIntervalsAndCannotRemoveCoverageForBookedVisits(){
        SchedulingAdminService admin=new SchedulingAdminService(jdbc,"Asia/Ho_Chi_Minh");
        var request=new SchedulingAdminModels.WorkingHourRequest(DOCTOR,1,LocalTime.of(8,0),LocalTime.of(12,0),LocalDate.of(2030,1,7),null);
        UUID hourId=transaction.execute(status->admin.addWorkingHour(request,ACTOR,"admin-test").id());
        ApiException duplicate=assertThrows(ApiException.class,()->transaction.execute(status->admin.addWorkingHour(
                new SchedulingAdminModels.WorkingHourRequest(DOCTOR,1,LocalTime.of(11,0),LocalTime.of(13,0),LocalDate.of(2030,1,7),null),ACTOR,"admin-test")));
        assertEquals("WORKING_HOURS_OVERLAP",duplicate.errorCode());

        insert(UUID.randomUUID(),Instant.parse("2030-01-07T01:00:00Z"),30);
        ApiException booked=assertThrows(ApiException.class,()->transaction.execute(status->{admin.deleteWorkingHour(hourId,ACTOR,"admin-test");return null;}));
        assertEquals("WORKING_HOUR_HAS_APPOINTMENTS",booked.errorCode());
    }

    @Test void approvedLeaveAndClinicClosureCannotInvalidateBookedVisits(){
        SchedulingAdminService admin=new SchedulingAdminService(jdbc,"Asia/Ho_Chi_Minh");
        Instant visit=Instant.parse("2030-01-07T01:00:00Z");
        insert(UUID.randomUUID(),visit,30);
        UUID leaveId=UUID.randomUUID();
        jdbc.update("INSERT INTO doctor_leave(id,doctor_id,starts_at,ends_at,status,reason) VALUES (?,?,?,?,'PENDING','Test')",
                leaveId,DOCTOR,java.sql.Timestamp.from(visit.minusSeconds(900)),java.sql.Timestamp.from(visit.plusSeconds(2700)));
        ApiException leave=assertThrows(ApiException.class,()->transaction.execute(status->admin.reviewLeave(leaveId,
                new SchedulingAdminModels.LeaveReviewRequest(SchedulingAdminModels.LeaveReviewRequest.LeaveDecision.APPROVE,null),ACTOR,"admin-test")));
        assertEquals("DOCTOR_LEAVE_CONFLICTS_WITH_APPOINTMENTS",leave.errorCode());

        ApiException closure=assertThrows(ApiException.class,()->transaction.execute(status->admin.addClosure(
                new SchedulingAdminModels.ClosureRequest(visit.minusSeconds(900),visit.plusSeconds(2700),"Bảo trì"),ACTOR,"admin-test")));
        assertEquals("CLOSURE_CONFLICTS_WITH_APPOINTMENTS",closure.errorCode());
    }

    private UUID insert(UUID patient,Instant at,int duration){
        return insert(patient,at,duration,DOCTOR,CHAIR);
    }
    private UUID insert(UUID patient,Instant at,int duration,UUID doctor,UUID chair){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO appointments(id,appointment_number,patient_id,doctor_id,chair_id,scheduled_at,duration_minutes,created_by) VALUES (?,?,?,?,?,?,?,?)",id,"AP-"+id,patient,doctor,chair,at.atOffset(ZoneOffset.UTC),duration,ACTOR);return id;
    }
    private void lockResourceKeys(String... keys){
        for(String key:keys)jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
            try(var statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")){
                statement.setString(1,key);statement.execute();
            }
            return null;
        });
    }
    private static String sqlState(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof SQLException sql)return sql.getSQLState();
        return "unknown";
    }
}

