package vn.phongkham.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class GatewaySecurityConfiguration {
    @Bean ReactiveJwtDecoder jwtDecoder(@Value("${security.jwks-uri}")String uri,@Value("${security.issuer}")String issuer,@Value("${security.audience}")String audience){
        var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(uri).build();
        OAuth2TokenValidator<Jwt> audienceValidator=jwt->jwt.getAudience().contains(audience)?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid audience",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceValidator));
        return decoder;
    }
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http,ReactiveJwtDecoder decoder){
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange->exchange.pathMatchers("/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/logout","/.well-known/jwks.json","/actuator/health/**").permitAll().anyExchange().authenticated())
                .oauth2ResourceServer(oauth->oauth.jwt(jwt->jwt.jwtDecoder(decoder))).build();
    }
}

