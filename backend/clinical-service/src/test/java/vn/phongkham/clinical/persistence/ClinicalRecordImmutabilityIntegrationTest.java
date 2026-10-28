package vn.phongkham.clinical.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class ClinicalRecordImmutabilityIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18.6-alpine");
    static JdbcTemplate jdbc;

    @BeforeAll static void migrate(){
        DataSource dataSource=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        jdbc=new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach void reset(){jdbc.execute("TRUNCATE clinical_records CASCADE");}

    @Test void signedRecordAndItsTeethCannotBeChanged(){
        UUID record=createRecord(),tooth=UUID.randomUUID();
        jdbc.update("INSERT INTO dental_record_teeth(id,record_id,tooth_number,condition_code) VALUES (?,?,36,'CARIES')",tooth,record);
        sign(record);

        assertSqlState("55000",()->jdbc.update("UPDATE clinical_records SET diagnosis='changed' WHERE id=?",record));
        assertSqlState("55000",()->jdbc.update("UPDATE dental_record_teeth SET condition_code='HEALTHY' WHERE id=?",tooth));
        assertSqlState("55000",()->jdbc.update("DELETE FROM dental_record_teeth WHERE id=?",tooth));
        assertSqlState("55000",()->jdbc.update("INSERT INTO dental_record_teeth(record_id,tooth_number,condition_code) VALUES (?,37,'HEALTHY')",record));
    }

    @Test void issuedPrescriptionAndItsItemsCannotBeChanged(){
        UUID record=createRecord(),prescription=UUID.randomUUID(),item=UUID.randomUUID();
        sign(record);
        jdbc.update("INSERT INTO prescriptions(id,prescription_number,patient_id,record_id,doctor_id,status) VALUES (?,? ,(SELECT patient_id FROM clinical_records WHERE id=?),?,(SELECT doctor_id FROM clinical_records WHERE id=?),'DRAFT')",
                prescription,"RX-"+prescription,record,record,record);
        jdbc.update("INSERT INTO prescription_items(id,prescription_id,medication_name,dosage,route,frequency,duration_days,quantity) VALUES (?,?, 'Amoxicillin','500 mg','ORAL','BID',5,'10')",item,prescription);
        jdbc.update("UPDATE prescriptions SET status='ISSUED' WHERE id=?",prescription);

        assertSqlState("55000",()->jdbc.update("UPDATE prescriptions SET notes='changed' WHERE id=?",prescription));
        assertSqlState("55000",()->jdbc.update("DELETE FROM prescriptions WHERE id=?",prescription));
        assertSqlState("55000",()->jdbc.update("UPDATE prescription_items SET dosage='1 g' WHERE id=?",item));
        assertSqlState("55000",()->jdbc.update("DELETE FROM prescription_items WHERE id=?",item));
    }

    @Test void prescriptionsRequireASignedParentRecord(){
        UUID draft=createRecord(),prescription=UUID.randomUUID();
        assertSqlState("23514",()->jdbc.update("INSERT INTO prescriptions(id,prescription_number,patient_id,record_id,doctor_id) VALUES (?,? ,(SELECT patient_id FROM clinical_records WHERE id=?),?,(SELECT doctor_id FROM clinical_records WHERE id=?))",
                prescription,"RX-"+prescription,draft,draft,draft));
    }

    @Test void approvedTreatmentPlanAndItsItemsCannotBeChanged(){
        UUID record=createRecord(),plan=UUID.randomUUID(),item=UUID.randomUUID();
        sign(record);
        jdbc.update("INSERT INTO treatment_plans(id,patient_id,clinical_record_id,doctor_id,title) SELECT ?,patient_id,id,doctor_id,'Plan' FROM clinical_records WHERE id=?",plan,record);
        jdbc.update("INSERT INTO treatment_plan_items(id,plan_id,sequence_no,service_id,title_snapshot,unit_price) VALUES (?,?,1,?,'Cleaning',100000)",item,plan,UUID.randomUUID());
        jdbc.update("UPDATE treatment_plans SET status='APPROVED',approved_at=now() WHERE id=?",plan);

        assertSqlState("55000",()->jdbc.update("UPDATE treatment_plans SET title='changed' WHERE id=?",plan));
        assertSqlState("55000",()->jdbc.update("DELETE FROM treatment_plans WHERE id=?",plan));
        assertSqlState("55000",()->jdbc.update("UPDATE treatment_plan_items SET notes='changed' WHERE id=?",item));
        assertSqlState("55000",()->jdbc.update("INSERT INTO treatment_plan_items(plan_id,sequence_no,service_id,title_snapshot,unit_price) VALUES (?,2,?,'New step',1)",plan,UUID.randomUUID()));
    }

    private UUID createRecord(){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO clinical_records(id,record_number,patient_id,appointment_id,doctor_id,visit_at) VALUES (?,?,?,?,?,now())",
                id,"HS-"+id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        return id;
    }

    private void sign(UUID id){jdbc.update("UPDATE clinical_records SET status='SIGNED',signed_at=now(),signed_by=doctor_id WHERE id=?",id);}

    private static void assertSqlState(String expected,org.junit.jupiter.api.function.Executable action){
        DataAccessException error=assertThrows(DataAccessException.class,action);
        assertEquals(expected,sqlState(error));
    }

    private static String sqlState(Throwable error){
        for(Throwable current=error;current!=null;current=current.getCause())if(current instanceof SQLException sql)return sql.getSQLState();
        return "unknown";
    }
}
