package vn.phongkham.patient.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.phongkham.common.ApiException;
import vn.phongkham.patient.application.IdentityLookupClient;
import vn.phongkham.patient.application.PatientService;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientControllerAuthorizationTest {
    @Test void patientCannotEditDeleteOrArchiveAdministrativeRecords() {
        var service=mock(PatientService.class);
        var controller=new PatientController(service,mock(IdentityLookupClient.class));
        var jwt=Jwt.withTokenValue("token").header("alg","RS256").subject(UUID.randomUUID().toString()).claim("roles",List.of("PATIENT")).build();
        var request=new MockHttpServletRequest();UUID id=UUID.randomUUID();
        assertThrows(ApiException.class,()->controller.update(id,null,jwt,request));
        assertThrows(ApiException.class,()->controller.delete(id,jwt,request));
        assertThrows(ApiException.class,()->controller.archive(id,jwt,request));
        assertThrows(ApiException.class,()->controller.restore(id,jwt,request));
        verifyNoInteractions(service);
    }
    @Test void accountWithoutRolesCannotReadPatientRecord() {
        var service=mock(PatientService.class);
        var controller=new PatientController(service,mock(IdentityLookupClient.class));
        var jwt=Jwt.withTokenValue("token").header("alg","RS256").subject(UUID.randomUUID().toString()).claim("roles",List.of()).build();
        assertThrows(ApiException.class,()->controller.get(UUID.randomUUID(),jwt));verifyNoInteractions(service);
    }
}
