package de.julith.opencrm.shared.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * JwtDecoder mit Issuer-Validierung (aus spring.security...issuer-uri) und optionaler
 * Audience-Pruefung (docs/05 Abschnitt 3.2): ist opencrm.auth.expected-audience gesetzt,
 * werden nur Tokens mit passendem aud-Claim akzeptiert.
 *
 * Der Decoder wird über SupplierJwtDecoder LAZY erzeugt (wie Spring Boot selbst): die
 * Issuer-Metadaten werden erst beim ersten echten Token abgerufen, nicht beim Kontextstart —
 * so startet das Backend auch, wenn Keycloak noch nicht erreichbar ist, und Tests mit
 * Mock-JWTs benötigen keinen echten Issuer.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties,
                          @Value("${opencrm.auth.expected-audience:}") String expectedAudience) {
        String issuerUri = properties.getJwt().getIssuerUri();
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
            if (expectedAudience != null && !expectedAudience.isBlank()) {
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        withIssuer, audienceValidator(expectedAudience)));
            } else {
                decoder.setJwtValidator(withIssuer);
            }
            return decoder;
        });
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        OAuth2Error error = new OAuth2Error("invalid_token",
                "Erwartete Audience " + expectedAudience + " fehlt im Token", null);
        return jwt -> {
            List<String> audiences = jwt.getAudience();
            if (audiences != null && audiences.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}
