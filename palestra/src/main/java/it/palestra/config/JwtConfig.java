/*
 * FILE: JwtConfig.java
 * A COSA SERVE: prepara gli strumenti per i token JWT della API: la CHIAVE segreta, il "codificatore" che crea e
 * firma i token (al login) e il "decodificatore" che ne verifica firma e scadenza (a ogni richiesta alla API).
 *
 * COS'È UN JWT (JSON Web Token): una stringa in tre parti separate da punti: intestazione . contenuto . firma.
 *   - il CONTENUTO ("claims") dice chi sei (subject = email), i tuoi ruoli e quando scade il token;
 *   - la FIRMA è calcolata con la chiave segreta del server: chi non la conosce non può creare un token valido né
 *     modificarne il contenuto (se cambiasse un solo carattere, la firma non tornerebbe più).
 * Il server NON ricorda i token emessi (niente sessione): a ogni richiesta ricalcola la firma e controlla la scadenza.
 * ATTENZIONE: il contenuto è solo codificato, NON cifrato: chiunque può leggerlo (provalo su jwt.io). Per questo
 * dentro non si mette mai nulla di segreto (niente password).
 */
package it.palestra.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import it.palestra.repository.UtenteRepository;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    // La chiave segreta per firmare i token, letta da application.properties (app.jwt.secret, o dalla variabile
    // d'ambiente JWT_SECRET). HS256 (HMAC con SHA-256) richiede una chiave di almeno 32 byte: con una più corta
    // l'applicazione NON parte, invece di funzionare in modo poco sicuro.
    @Bean
    SecretKey chiaveJwt(@Value("${app.jwt.secret}") String segreto) {
        byte[] byteChiave = segreto.getBytes(StandardCharsets.UTF_8);
        if (byteChiave.length < 32) {
            throw new IllegalStateException("app.jwt.secret deve avere almeno 32 caratteri (256 bit)");
        }
        return new SecretKeySpec(byteChiave, "HmacSHA256");
    }

    // Il codificatore: crea e FIRMA i token (lo usa JwtService al login).
    @Bean
    JwtEncoder jwtEncoder(SecretKey chiave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chiave));
    }

    // Il decodificatore: a ogni richiesta alla API verifica la firma (con la stessa chiave) e la scadenza. Un token
    // modificato, firmato con un'altra chiave, scaduto o REVOCATO (vedi ValidatoreRevocaToken) viene rifiutato (risposta 401).
    @Bean
    JwtDecoder jwtDecoder(SecretKey chiave, UtenteRepository utenti) {
        NimbusJwtDecoder decodificatore = NimbusJwtDecoder.withSecretKey(chiave).macAlgorithm(MacAlgorithm.HS256).build();
        // Controlli sul contenuto, in ordine: quelli standard (scadenza, "non prima di") + la revoca (ValidatoreRevocaToken).
        decodificatore.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new ValidatoreRevocaToken(utenti)));
        return decodificatore;
    }
}
