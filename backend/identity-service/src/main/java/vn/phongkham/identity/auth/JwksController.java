package vn.phongkham.identity.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {
    private final RSAKey signingKey;
    public JwksController(RSAKey signingKey) { this.signingKey=signingKey; }
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<?> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .body(new JWKSet(signingKey.toPublicJWK()).toJSONObject());
    }
}
